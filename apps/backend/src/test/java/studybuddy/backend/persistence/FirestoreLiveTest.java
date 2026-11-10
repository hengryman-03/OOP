package studybuddy.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import studybuddy.backend.auth.FirebaseConfig;
import studybuddy.backend.student.model.Course;

import java.util.Map;
import java.util.UUID;

/**
 * Runs the real app against the Firebase project's Cloud Firestore. Opt-in, because it needs the
 * Firebase Admin credential and internet: {@code FIRESTORE_LIVE_TEST=true ./mvnw test}.
 *
 * <p>All data goes into collections with a random {@code livetest_...} prefix, which are deleted
 * afterwards, so the team's real Firestore data is never touched.
 */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_LIVE_TEST", matches = "true")
@SpringBootTest(properties = "app.storage=firestore")
@ActiveProfiles("firestore")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirestoreLiveTest {
    private static final String PREFIX =
            "livetest_" + UUID.randomUUID().toString().substring(0, 8) + "_";

    @DynamicPropertySource
    static void isolatedCollections(DynamicPropertyRegistry registry) {
        registry.add("app.firestore.collection-prefix", () -> PREFIX);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Firestore firestore;
    @Autowired StudyRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean FirebaseConfig firebaseConfig;

    @BeforeEach
    void treatIdTokenAsUid() {
        doAnswer(invocation -> invocation.getArgument(0))
                .when(firebaseConfig)
                .verifiedUid(anyString());
    }

    @Test
    void usesFirestoreAndSeedsTheDemoData() throws Exception {
        assertInstanceOf(FirestoreStudyRepository.class, repository);
        MockHttpSession admin = adminLogin();
        assertEquals(51, call(admin, get("/api/admin/accounts"), null, 200).size());
        assertTrue(document("StudentProfile", "S050").get().get().exists());
    }

    @Test
    void acceptedBuddyRequestIsStoredInFirestore() throws Exception {
        MockHttpSession a = studentLogin("S001"), b = studentLogin("S002");
        String id =
                call(
                                a,
                                post("/api/connections/requests"),
                                Map.of("receiverId", "S002", "message", "Live Firestore check"),
                                200)
                        .get("id")
                        .asText();
        call(b, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), null, 200);

        assertEquals(
                "ACCEPTED", document("BuddyRequest", id).get().get().getString("status"));
        assertFalse(
                call(a, get("/api/students/S002"), null, 200).get("contactNumber").isNull());
    }

    @Test
    void failedTransactionSendsNothingToFirestore() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    repository.lock();
                                    repository.save("LIVE1", new Course("LIVE1", "Rolled back"));
                                    assertTrue(
                                            repository.find(Course.class, "LIVE1").isPresent(),
                                            "the transaction sees its own staged write");
                                    throw new IllegalStateException("business rule failed");
                                }));

        assertFalse(document("Course", "LIVE1").get().get().exists());
        assertTrue(repository.find(Course.class, "LIVE1").isEmpty());
    }

    @Test
    void changeMadeByAnotherBackendIsVisibleOnTheNextRead() throws Exception {
        assertTrue(repository.find(Course.class, "OTHER1").isEmpty());
        // Simulates another laptop's backend writing straight to Firestore.
        document("Course", "OTHER1").set(Map.of("code", "OTHER1", "title", "From laptop B")).get();

        long deadline = System.currentTimeMillis() + 10_000;
        while (repository.find(Course.class, "OTHER1").isEmpty()
                && System.currentTimeMillis() < deadline) Thread.sleep(100);
        assertEquals(
                "From laptop B",
                repository.find(Course.class, "OTHER1").orElseThrow().getTitle());
    }

    @AfterAll
    void deleteLiveTestCollections() throws Exception {
        for (CollectionReference collection : firestore.listCollections()) {
            if (!collection.getId().startsWith(PREFIX)) continue;
            for (DocumentReference doc : collection.listDocuments()) doc.delete().get();
        }
    }

    private DocumentReference document(String kind, String id) {
        return firestore.collection(PREFIX + kind).document(id);
    }

    private MockHttpSession adminLogin() throws Exception {
        return login(
                post("/api/session/admin-login"),
                Map.of("username", "admin", "password", "admin123"));
    }

    private MockHttpSession studentLogin(String id) throws Exception {
        return login(post("/api/session/login"), Map.of("idToken", id));
    }

    private MockHttpSession login(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return (MockHttpSession)
                mvc.perform(
                                request.header("X-StudyBuddy-Request", "1")
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession();
    }

    private JsonNode call(
            MockHttpSession session, MockHttpServletRequestBuilder request, Object body, int code)
            throws Exception {
        request.session(session)
                .header("X-StudyBuddy-Request", "1")
                .contentType("application/json");
        if (body != null) request.content(json.writeValueAsString(body));
        return json.readTree(
                mvc.perform(request)
                        .andExpect(status().is(code))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }
}
