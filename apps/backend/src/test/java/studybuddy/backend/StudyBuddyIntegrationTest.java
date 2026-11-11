package studybuddy.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.auth.FirebaseConfig;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.matching.service.AvailabilityCalculator;
import studybuddy.backend.matching.service.MatchingService;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.model.AvailabilitySlot;
import studybuddy.backend.student.model.StudentProfile;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** HTTP integration tests use real H2 and sessions; only external Firebase token verification is stubbed. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studybuddy-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class StudyBuddyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudyRepository repository;
    @Autowired MatchingService matching;
    @MockitoBean FirebaseConfig firebaseConfig;

    @BeforeEach
    void stubVerifiedFirebaseUid() {
        when(firebaseConfig.verifiedUid(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    MockHttpSession login(String id) throws Exception {
        boolean admin = "ADMIN001".equals(id);
        MvcResult result =
                mvc.perform(
                                post(admin ? "/api/session/admin-login" : "/api/session/login")
                                        .header("X-StudyBuddy-Request", "1")
                                        .contentType("application/json")
                                        .content(
                                                admin
                                                        ? "{\"username\":\"admin\",\"password\":\"admin123\"}"
                                                        : json.writeValueAsString(
                                                                java.util.Map.of("idToken", id))))
                        .andExpect(status().isOk())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    JsonNode call(
            MockHttpSession session,
            MockHttpServletRequestBuilder request,
            Object body,
            int expected)
            throws Exception {
        request.session(session)
                .header("X-StudyBuddy-Request", "1")
                .contentType("application/json");
        if (body != null) request.content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    JsonNode call(MockHttpSession s, MockHttpServletRequestBuilder r, int code) throws Exception {
        return call(s, r, null, code);
    }

    ObjectNode profile(MockHttpSession s, String id) throws Exception {
        return (ObjectNode) call(s, get("/api/students/" + id), 200);
    }

    String request(MockHttpSession sender, String receiver) throws Exception {
        return call(
                        sender,
                        post("/api/connections/requests"),
                        java.util.Map.of(
                                "receiverId", receiver, "message", "Let us study together"),
                        200)
                .get("id")
                .asText();
    }

    ObjectNode groupInput(MockHttpSession s) throws Exception {
        return (ObjectNode) call(s, get("/api/groups"), 200).get(0).deepCopy();
    }

    String newGroup(MockHttpSession s, int size) throws Exception {
        ObjectNode group = groupInput(s);
        group.put("maximumGroupSize", size);
        return call(s, post("/api/groups"), group, 200).get("id").asText();
    }

    String join(MockHttpSession s, String group) throws Exception {
        return call(s, post("/api/groups/" + group + "/join-requests"), 200).get("id").asText();
    }

    @Test
    void seedsAndSessionBoundaries() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/session/login")
                                .contentType("application/json")
                                .content("{\"idToken\":\"S001\"}"))
                .andExpect(status().isForbidden());
        when(firebaseConfig.verifiedUid("invalid"))
                .thenThrow(DomainException.forbidden("Invalid or expired Firebase token."));
        mvc.perform(
                        post("/api/session/login")
                                .header("X-StudyBuddy-Request", "1")
                                .contentType("application/json")
                                .content("{\"idToken\":\"invalid\"}"))
                .andExpect(status().isForbidden());
        var student = login("S001");
        assertFalse(call(student, get("/api/session"), 200).get("lastActiveAt").isNull());
        assertEquals(50, call(student, get("/api/students"), 200).size());
        assertEquals(10, call(student, get("/api/students/courses"), 200).size());
        call(student, get("/api/admin/accounts"), 403);
        call(student, post("/api/admin/accounts/invitations"),
                java.util.Map.of("name", "Someone", "email", "someone@example.com"), 403);
        verify(firebaseConfig, never()).createStudent(anyString(), anyString());
        call(student, get("/api/admin/matching-config"), 403);
        call(student, get("/api/connections/students/S002"), 403);
        call(student, get("/api/students/S002/matches"), 403);
        var admin = login("ADMIN001");
        assertFalse(call(admin, get("/api/session"), 200).get("lastActiveAt").isNull());
        assertEquals(51, call(admin, get("/api/admin/accounts"), 200).size());
    }

    @Test
    void firebaseSignupUsesVerifiedUidAndCreatesAnIndependentSession() throws Exception {
        when(firebaseConfig.verifiedUid("signed-token")).thenReturn("firebase-uid-001");
        MvcResult signup =
                mvc.perform(
                                post("/api/session/signup")
                                        .header("X-StudyBuddy-Request", "1")
                                        .contentType("application/json")
                                        .content("{\"idToken\":\"signed-token\",\"name\":\"New learner\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode account = json.readTree(signup.getResponse().getContentAsString());
        assertEquals("firebase-uid-001", account.get("id").asText());
        assertEquals("STUDENT", account.get("role").asText());
        assertFalse(account.get("lastActiveAt").isNull());
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession();
        assertEquals(
                "firebase-uid-001",
                call(session, get("/api/session"), 200).get("id").asText());
        call(session, get("/api/students/firebase-uid-001"), 404);
        call(
                session,
                post("/api/session/signup"),
                java.util.Map.of("idToken", "signed-token", "name", "Duplicate"),
                409);
        assertEquals(
                "firebase-uid-001",
                call(session, post("/api/session/login"), java.util.Map.of("idToken", "signed-token"), 200)
                        .get("id")
                        .asText());
    }

    @Test
    void profilePrivacyFollowsAcceptanceAndEndingInBothDirections() throws Exception {
        var a = login("S001");
        var b = login("S002");
        var outsider = login("S003");
        assertFalse(profile(a, "S001").get("contactNumber").isNull());
        assertTrue(profile(a, "S002").get("contactNumber").isNull());
        String id = request(a, "S002");
        call(a, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 403);
        call(outsider, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 403);
        call(b, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 200);
        assertFalse(profile(a, "S002").get("contactNumber").isNull());
        assertFalse(profile(b, "S001").get("contactNumber").isNull());
        assertTrue(profile(outsider, "S002").get("contactNumber").isNull());
        for (JsonNode p : call(a, get("/api/students"), 200))
            assertTrue(p.get("contactNumber").isNull());
        for (JsonNode m : call(a, get("/api/students/S001/matches"), 200))
            assertTrue(m.get("student").get("contactNumber").isNull());
        call(b, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 409);
        call(a, post("/api/connections/requests/" + id + "/status?status=ENDED"), 200);
        assertTrue(profile(a, "S002").get("contactNumber").isNull());
        assertTrue(profile(b, "S001").get("contactNumber").isNull());
        call(b, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 409);
    }

    @Test
    void matchesHidePendingAndConnectedStudentsUntilTheRequestCloses() throws Exception {
        var admin = login("ADMIN001");
        ObjectNode config = (ObjectNode) call(admin, get("/api/admin/matching-config"), 200);
        config.put("maxResults", 50);
        call(admin, put("/api/admin/matching-config"), config, 200);
        var a = login("S001");
        var b = login("S002");
        assertTrue(matchIds(a, "S001").contains("S002"));
        String id = request(a, "S002");
        assertFalse(matchIds(a, "S001").contains("S002"));
        assertFalse(matchIds(b, "S002").contains("S001"));
        call(b, post("/api/connections/requests/" + id + "/status?status=ACCEPTED"), 200);
        assertFalse(matchIds(a, "S001").contains("S002"));
        call(a, post("/api/connections/requests/" + id + "/status?status=ENDED"), 200);
        assertTrue(matchIds(a, "S001").contains("S002"));
        assertTrue(matchIds(b, "S002").contains("S001"));
        call(b, post("/api/connections/requests/" + request(a, "S002") + "/status?status=DECLINED"), 200);
        assertTrue(matchIds(a, "S001").contains("S002"));
    }

    java.util.List<String> matchIds(MockHttpSession session, String id) throws Exception {
        var ids = new java.util.ArrayList<String>();
        for (JsonNode m : call(session, get("/api/students/" + id + "/matches"), 200))
            ids.add(m.get("student").get("id").asText());
        return ids;
    }

    @Test
    void preventsDuplicateReverseSelfAndInvalidBuddyRequests() throws Exception {
        var a = login("S001");
        var b = login("S002");
        call(a, post("/api/connections/requests"), java.util.Map.of("receiverId", "S001"), 400);
        call(a, post("/api/connections/requests"), java.util.Map.of("receiverId", "missing"), 404);
        call(
                a,
                post("/api/connections/requests"),
                java.util.Map.of("receiverId", "S002", "message", "x".repeat(501)),
                400);
        String id = request(a, "S002");
        call(b, post("/api/connections/requests"), java.util.Map.of("receiverId", "S001"), 409);
        call(a, post("/api/connections/requests/" + id + "/status?status=ENDED"), 409);
        call(b, post("/api/connections/requests/" + id + "/status?status=PENDING"), 400);
        call(b, post("/api/connections/requests/" + id + "/status?status=DECLINED"), 200);
        assertNotEquals(id, request(a, "S002"));
    }

    @Test
    void inputCannotSpoofRequestSender() throws Exception {
        var a = login("S001");
        JsonNode result =
                call(
                        a,
                        post("/api/connections/requests"),
                        java.util.Map.of(
                                "senderId", "S003", "receiverId", "S002", "status", "ACCEPTED"),
                        200);
        assertEquals("S001", result.get("senderId").asText());
        assertEquals("PENDING", result.get("status").asText());
    }

    @Test
    void savesOwnProfileAndRejectsInvalidNestedInput() throws Exception {
        var a = login("S001");
        ObjectNode p = profile(a, "S001");
        p.put("name", "Updated student");
        call(a, post("/api/students"), p, 200);
        assertEquals("Updated student", profile(a, "S001").get("name").asText());
        assertEquals("Updated student", call(a, get("/api/session"), 200).get("name").asText());
        p.put("id", "S002");
        call(a, post("/api/students"), p, 403);
        p.put("id", "S001");
        p.put("name", " ");
        call(a, post("/api/students"), p, 400);
        p.put("name", "Valid");
        ((ObjectNode) p.get("preference")).put("courseCode", "INVALID");
        call(a, post("/api/students"), p, 400);
        p = profile(a, "S001");
        ((ObjectNode) p.get("preference").get("availability").get(0)).put("endTime", "01:00");
        call(a, post("/api/students"), p, 400);
        p = profile(a, "S001");
        p.putNull("preference");
        call(a, post("/api/students"), p, 400);
        p = profile(a, "S001");
        ((ObjectNode) p.get("preference")).put("preferredGroupSize", 4);
        call(a, post("/api/students"), p, 400);
        call(a, get("/api/students/does-not-exist"), 404);
    }

    @Test
    void courseGoalModeAndAvailabilityFiltersApplyTogether() throws Exception {
        var a = login("S001");
        JsonNode result =
                call(
                        a,
                        get(
                                "/api/students/S001/matches?courseCode=IS442&studyGoal=CONCEPT_REVIEW&studyMode=IN_PERSON&day=WEDNESDAY&startTime=19:00&endTime=21:00&overlapOnly=true"),
                        200);
        assertTrue(result.size() > 0);
        int previous = 101;
        for (JsonNode row : result) {
            int score = row.get("score").asInt();
            assertTrue(score <= previous && score >= 0);
            previous = score;
            assertNotEquals("S001", row.get("student").get("id").asText());
            var preference = row.get("student").get("preference");
            assertEquals("CONCEPT_REVIEW", preference.get("studyGoal").asText());
            assertTrue(
                    List.of("IN_PERSON", "EITHER")
                            .contains(preference.get("preferredMode").asText()));
        }
        call(a, get("/api/students/S001/matches?startTime=19:00"), 400);
        call(a, get("/api/students/S001/matches?studyMode=invalid"), 400);
        call(a, get("/api/students/S001/matches?courseCode=invalid"), 400);
        assertEquals(0, call(a, get("/api/students/S001/matches?day=SUNDAY"), 200).size());
    }

    @Test
    void matchingWeightsAndStrategiesAffectScoresAndPersist() throws Exception {
        var admin = login("ADMIN001");
        var student = login("S001");
        ObjectNode config = (ObjectNode) call(admin, get("/api/admin/matching-config"), 200);
        String before = call(student, get("/api/students/S001/matches"), 200).toString();
        config.put("strategy", "AVAILABILITY_FIRST");
        call(admin, put("/api/admin/matching-config"), config, 200);
        JsonNode availabilityResults = call(student, get("/api/students/S001/matches"), 200);
        assertNotEquals(before, availabilityResults.toString());
        assertTrue(availabilityResults.get(0).get("rankingReason").asText().contains("shared weekly minutes"));
        assertEquals(
                "AVAILABILITY_FIRST",
                repository.find(MatchingProperties.class, "current").orElseThrow().getStrategy());
        config.put("strategy", "COURSE_FIRST");
        call(admin, put("/api/admin/matching-config"), config, 200);
        assertEquals(
                100,
                java.util.Arrays.stream(matching.effectiveWeights(matching.getCurrentConfig()))
                        .sum());
        config.put("strategy", "PARETO");
        call(admin, put("/api/admin/matching-config"), config, 200);
        assertTrue(
                call(student, get("/api/students/S001/matches"), 200)
                        .get(0)
                        .get("rankingReason")
                        .asText()
                        .contains("Pareto front"));
        config.put("strategy", "UNKNOWN");
        call(admin, put("/api/admin/matching-config"), config, 400);
        config.put("strategy", "PARETO");
        for (String key :
                List.of(
                        "courseWeight",
                        "availabilityWeight",
                        "studyModeWeight",
                        "studyGoalWeight",
                        "groupSizeWeight")) config.put(key, 0);
        call(admin, put("/api/admin/matching-config"), config, 400);
        config.put("courseWeight", -1);
        call(admin, put("/api/admin/matching-config"), config, 400);
        config.put("courseWeight", 100);
        config.put("maxResults", 0);
        call(admin, put("/api/admin/matching-config"), config, 400);
        config.put("maxResults", 10);
        call(student, put("/api/admin/matching-config"), config, 403);
    }

    @Test
    void availabilityUsesUniqueMinutesAndDoesNotCountTouchingSlots() {
        var a = new AvailabilitySlot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), LocalTime.of(21, 0));
        var b = new AvailabilitySlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), LocalTime.of(22, 0));
        var c = new AvailabilitySlot(DayOfWeek.WEDNESDAY, LocalTime.of(21, 0), LocalTime.of(22, 0));
        assertEquals(120, AvailabilityCalculator.minutes(List.of(a, a)).cardinality());
        assertEquals(60, AvailabilityCalculator.overlap(List.of(a, a), List.of(b, b)));
        assertEquals(0, AvailabilityCalculator.overlap(List.of(a), List.of(c)));
    }

    @Test
    void groupCreationIgnoresSpoofedOwnershipAndUpdatePreservesMembership() throws Exception {
        var a = login("S001");
        var b = login("S002");
        ObjectNode input = groupInput(a);
        input.put("leaderId", "S002");
        input.put("status", "CLOSED");
        JsonNode created = call(a, post("/api/groups"), input, 200);
        String id = created.get("id").asText();
        assertEquals("S001", created.get("leaderId").asText());
        assertEquals(1, created.get("memberIds").size());
        assertEquals("ACTIVE", created.get("status").asText());
        call(b, put("/api/groups/" + id), input, 403);
        JsonNode updated = call(a, put("/api/groups/" + id), input, 200);
        assertEquals("S001", updated.get("leaderId").asText());
        assertEquals(1, updated.get("memberIds").size());
        input.put("maximumGroupSize", 0);
        call(a, post("/api/groups"), input, 400);
    }

    @Test
    void membershipRequiresLeaderAndRechecksCapacityAtAcceptance() throws Exception {
        var a = login("S001");
        var b = login("S002");
        var c = login("S003");
        String group = newGroup(a, 2), rb = join(b, group), rc = join(c, group);
        call(b, post("/api/groups/" + group + "/join-requests"), 409);
        call(c, post("/api/groups/join-requests/" + rb + "/decision?decision=ACCEPTED"), 403);
        call(a, post("/api/groups/join-requests/" + rb + "/decision?decision=ACCEPTED"), 200);
        call(a, post("/api/groups/join-requests/" + rc + "/decision?decision=ACCEPTED"), 409);
        call(a, post("/api/groups/join-requests/" + rb + "/decision?decision=ACCEPTED"), 409);
        call(b, post("/api/groups/" + group + "/remove-member?studentId=S001"), 403);
        call(a, post("/api/groups/" + group + "/remove-member?studentId=S001"), 409);
        call(b, post("/api/groups/" + group + "/remove-member?studentId=S002"), 200);
        call(a, post("/api/groups/join-requests/" + rc + "/decision?decision=ACCEPTED"), 200);
    }

    @Test
    void leaderMustTransferOrCloseAndCannotTransferToSelfOrOutsider() throws Exception {
        var a = login("S001");
        var b = login("S002");
        String group = newGroup(a, 3), request = join(b, group);
        call(a, post("/api/groups/join-requests/" + request + "/decision?decision=ACCEPTED"), 200);
        call(a, post("/api/groups/" + group + "/leader-quits"), 409);
        call(a, post("/api/groups/" + group + "/leader-quits?replacementLeaderId=S001"), 400);
        call(a, post("/api/groups/" + group + "/transfer-leadership?newLeaderId=S003"), 400);
        JsonNode transferred =
                call(
                        a,
                        post("/api/groups/" + group + "/leader-quits?replacementLeaderId=S002"),
                        200);
        assertEquals("S002", transferred.get("leaderId").asText());
        assertEquals(1, transferred.get("memberIds").size());
        call(a, post("/api/groups/" + group + "/close"), 403);
        assertEquals(
                "CLOSED",
                call(b, post("/api/groups/" + group + "/leader-quits"), 200)
                        .get("status")
                        .asText());
    }

    @Test
    void closingGroupRejectsPendingRequestsAndPreventsNewChanges() throws Exception {
        var a = login("S001");
        var b = login("S002");
        var c = login("S003");
        String group = newGroup(a, 3), request = join(b, group);
        call(a, post("/api/groups/" + group + "/close"), 200);
        call(c, post("/api/groups/" + group + "/join-requests"), 409);
        call(a, post("/api/groups/join-requests/" + request + "/decision?decision=ACCEPTED"), 409);
        JsonNode history = call(b, get("/api/groups/join-requests"), 200);
        assertEquals("DECLINED", history.get(0).get("status").asText());
        call(a, put("/api/groups/" + group), groupInput(a), 409);
    }

    @Test
    void administratorsCanManageGroupsAndCannotOrphanActiveLeadership() throws Exception {
        var admin = login("ADMIN001");
        call(admin, delete("/api/admin/accounts/S001"), 409);
        call(
                admin,
                post("/api/admin/accounts"),
                java.util.Map.of(
                        "id",
                        "S001",
                        "name",
                        "Avery Tan",
                        "role",
                        "STUDENT",
                        "status",
                        "SUSPENDED"),
                409);
        call(admin, post("/api/groups/G001/transfer-leadership?newLeaderId=S011"), 200);
        call(admin, delete("/api/admin/accounts/S001"), 200);
        call(admin, post("/api/groups/G002/close"), 200);
        call(admin, delete("/api/admin/accounts/ADMIN001"), 409);
    }

    @Test
    void selfRegistrationProfileSetupSuspensionAndDeletion() throws Exception {
        var admin = login("ADMIN001");
        MvcResult signup =
                mvc.perform(
                                post("/api/session/signup")
                                        .header("X-StudyBuddy-Request", "1")
                                        .contentType("application/json")
                                        .content("{\"idToken\":\"firebase-uid-002\",\"name\":\"New learner\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode created = json.readTree(signup.getResponse().getContentAsString());
        String id = created.get("id").asText();
        var student = (MockHttpSession) signup.getRequest().getSession();
        call(student, get("/api/students/" + id), 404);
        ObjectNode p = profile(login("S001"), "S001");
        p.put("id", id);
        p.put("name", "New learner");
        call(student, post("/api/students"), p, 200);
        String buddy = request(student, "S004");
        assertNotNull(buddy);
        ObjectNode account = (ObjectNode) created;
        account.put("status", "SUSPENDED");
        call(admin, post("/api/admin/accounts"), account, 200);
        call(student, get("/api/students"), 403);
        account.put("status", "ACTIVE");
        call(admin, post("/api/admin/accounts"), account, 200);
        call(student, get("/api/students"), 200);
        call(admin, delete("/api/admin/accounts/" + id), 200);
        verify(firebaseConfig).deleteUser(id);
        call(student, get("/api/students"), 401);
        assertTrue(repository.find(StudentProfile.class, id).isEmpty());
        assertEquals(0, call(login("S004"), get("/api/connections/students/S004"), 200).size());
        when(firebaseConfig.verifiedUid(id))
                .thenThrow(DomainException.forbidden("Invalid or expired Firebase token."));
        call(admin, post("/api/session/signup"),
                java.util.Map.of("idToken", id, "name", "Returning learner"), 403);
        assertTrue(repository.find(StudentProfile.class, id).isEmpty());
    }

    @Test
    void administratorsInviteFirebaseStudentsAndKeepEmailOnUpdate() throws Exception {
        var admin = login("ADMIN001");
        when(firebaseConfig.createStudent("Invited Learner", "invited@example.com"))
                .thenReturn("invited-uid");
        JsonNode created = call(admin, post("/api/admin/accounts/invitations"),
                java.util.Map.of("name", " Invited Learner ", "email", "Invited@Example.com"), 200);
        assertEquals("invited-uid", created.get("id").asText());
        assertEquals("invited@example.com", created.get("email").asText());
        assertEquals("invited-uid", call(login("invited-uid"), get("/api/session"), 200).get("id").asText());
        ObjectNode edited = (ObjectNode) created.deepCopy();
        edited.remove("email");
        edited.put("name", "Invited Student");
        assertEquals("invited@example.com",
                call(admin, post("/api/admin/accounts"), edited, 200).get("email").asText());
        call(admin, delete("/api/admin/accounts/invited-uid"), 200);
        verify(firebaseConfig).deleteUser("invited-uid");
    }

    @Test
    void firebaseDeletionFailurePreservesApplicationAccount() throws Exception {
        var admin = login("ADMIN001");
        doThrow(new DomainException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Firebase unavailable."))
                .when(firebaseConfig).deleteUser("S050");
        call(admin, delete("/api/admin/accounts/S050"), 502);
        assertTrue(repository.find(studybuddy.backend.admin.model.UserAccount.class, "S050").isPresent());
    }

    @Test
    void administratorsCannotCreateAccountsThroughTheUpdateEndpoint() throws Exception {
        var admin = login("ADMIN001");
        call(
                admin,
                post("/api/admin/accounts"),
                java.util.Map.of(
                        "name", "New learner", "role", "STUDENT", "status", "ACTIVE"),
                400);
        call(
                admin,
                post("/api/admin/accounts"),
                java.util.Map.of(
                        "name", "Extra admin", "role", "SYSTEM_ADMINISTRATOR", "status", "ACTIVE"),
                400);
    }

    @Test
    void venueBookingRespectsMembershipCapacityAndConflicts() throws Exception {
        mvc.perform(get("/api/venues/rooms")).andExpect(status().isUnauthorized());
        var leader = login("S001");
        var anotherLeader = login("S002");
        var outsider = login("S004");
        String date =
                java.time.LocalDate.now()
                        .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .toString();
        var plan =
                java.util.Map.of(
                        "groupId", "G001",
                        "sessionDate", date,
                        "startTime", "10:00",
                        "endTime", "11:00",
                        "maxGroupSize", 4);
        String sessionId = call(leader, post("/api/sessions"), plan, 200).get("id").asText();
        String recommendations = "/api/venues/recommendations?sessionId=" + sessionId;
        call(outsider, get(recommendations), 403);
        JsonNode rooms = call(leader, get(recommendations), 200);
        assertTrue(rooms.size() > 0);
        for (JsonNode room : rooms) assertTrue(room.get("capacity").asInt() >= 4);
        String roomId = rooms.get(0).get("id").asText();
        call(leader, post("/api/venues/bookings?sessionId=" + sessionId + "&roomId=" + roomId), 200);
        assertEquals(
                roomId,
                call(leader, get("/api/sessions?groupId=G001"), 200)
                        .get(0)
                        .get("bookedRoomId")
                        .asText());
        for (JsonNode room : call(leader, get(recommendations), 200))
            assertNotEquals(roomId, room.get("id").asText());

        String competingSessionId =
                call(
                                anotherLeader,
                                post("/api/sessions"),
                                java.util.Map.of(
                                        "groupId", "G002",
                                        "sessionDate", date,
                                        "startTime", "10:00",
                                        "endTime", "11:00",
                                        "maxGroupSize", 4),
                                200)
                        .get("id")
                        .asText();
        call(
                anotherLeader,
                post("/api/venues/bookings?sessionId=" + competingSessionId + "&roomId=" + roomId),
                409);
        call(leader, post("/api/venues/bookings/release?sessionId=" + sessionId), 200);
        assertTrue(
                call(leader, get("/api/sessions?groupId=G001"), 200)
                        .get(0)
                        .get("bookedRoomId")
                        .isNull());
        call(
                anotherLeader,
                post("/api/venues/bookings?sessionId=" + competingSessionId + "&roomId=" + roomId),
                200);
    }

    String nextMonday() {
        return java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
                .toString();
    }

    String planSession(MockHttpSession leader, String group, String start, String end, int size)
            throws Exception {
        return call(
                        leader,
                        post("/api/sessions"),
                        java.util.Map.of(
                                "groupId", group,
                                "sessionDate", nextMonday(),
                                "startTime", start,
                                "endTime", end,
                                "maxGroupSize", size),
                        200)
                .get("id")
                .asText();
    }

    void accept(MockHttpSession leader, MockHttpSession student, String group) throws Exception {
        call(leader, post("/api/groups/join-requests/" + join(student, group) + "/decision?decision=ACCEPTED"), 200);
    }

    @Test
    void closingGroupCancelsItsSessionsAndReleasesTheirRooms() throws Exception {
        var leader = login("S001");
        var otherLeader = login("S002");
        String group = newGroup(leader, 3), session = planSession(leader, group, "10:00", "11:00", 2);
        call(leader, post("/api/venues/bookings?sessionId=" + session + "&roomId=R001"), 200);
        call(leader, post("/api/groups/" + group + "/close"), 200);
        assertEquals(0, call(leader, get("/api/sessions?groupId=" + group), 200).size());
        call(leader, post("/api/venues/bookings?sessionId=" + session + "&roomId=R002"), 404);
        String competing = planSession(otherLeader, "G002", "10:00", "11:00", 2);
        call(otherLeader, post("/api/venues/bookings?sessionId=" + competing + "&roomId=R001"), 200);

        String quitting = newGroup(leader, 3), lone = planSession(leader, quitting, "12:00", "13:00", 2);
        call(leader, post("/api/groups/" + quitting + "/leader-quits?closeGroup=true"), 200);
        assertTrue(repository.find(studybuddy.backend.session.model.StudySession.class, lone).isEmpty());
    }

    /** Leader plus two joiners, with the smallest suitable room booked for a Monday session. */
    record BookedGroup(String group, String session, String room, int seats) {}

    BookedGroup groupWithBookedRoom(MockHttpSession leader, MockHttpSession booker) throws Exception {
        String group = newGroup(leader, 12);
        accept(leader, login("S004"), group);
        accept(leader, login("S005"), group);
        String session = planSession(leader, group, "10:00", "11:00", 3);
        JsonNode room = call(leader, get("/api/venues/recommendations?sessionId=" + session), 200).get(0);
        call(booker, post("/api/venues/bookings?sessionId=" + session + "&roomId=" + room.get("id").asText()), 200);
        return new BookedGroup(group, session, room.get("id").asText(), room.get("capacity").asInt());
    }

    @Test
    void joiningIsRejectedWhenABookedRoomIsFullAndAllowedOnceReleased() throws Exception {
        var leader = login("S001");
        BookedGroup booked = groupWithBookedRoom(leader, leader);
        int members = 3;
        for (int i = 6; members < booked.seats(); i++, members++)
            accept(leader, login(String.format("S%03d", i)), booked.group());
        String request = join(login("S030"), booked.group());
        String rejection =
                call(leader, post("/api/groups/join-requests/" + request + "/decision?decision=ACCEPTED"), 409)
                        .toString();
        assertTrue(
                rejection.contains(
                        "A booked room for this group only seats "
                                + booked.seats()
                                + ". Release or rebook it before adding another member."),
                rejection);
        call(leader, post("/api/venues/bookings/release?sessionId=" + booked.session()), 200);
        call(leader, post("/api/groups/join-requests/" + request + "/decision?decision=ACCEPTED"), 200);
    }

    @Test
    void joiningIgnoresBookingsForSessionsInThePast() throws Exception {
        var leader = login("S001");
        BookedGroup booked = groupWithBookedRoom(leader, leader);
        int members = 3;
        for (int i = 6; members < booked.seats(); i++, members++)
            accept(leader, login(String.format("S%03d", i)), booked.group());
        var booking =
                repository.all(studybuddy.backend.venue.model.RoomBooking.class).stream()
                        .filter(b -> b.getSessionId().equals(booked.session()))
                        .findFirst()
                        .orElseThrow();
        booking.setDate(java.time.LocalDate.now().minusDays(1));
        repository.save(booking.getId(), booking);
        accept(leader, login("S030"), booked.group());
    }

    @Test
    void bookingsMadeByADepartingMemberPassToTheLeader() throws Exception {
        var leader = login("S001");
        var member = login("S004");
        BookedGroup booked = groupWithBookedRoom(leader, member);
        var booking =
                repository.all(studybuddy.backend.venue.model.RoomBooking.class).stream()
                        .filter(b -> b.getSessionId().equals(booked.session()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("S004", booking.getBookedBy());
        call(member, post("/api/groups/" + booked.group() + "/remove-member?studentId=S004"), 200);
        assertEquals(
                "S001",
                repository
                        .find(studybuddy.backend.venue.model.RoomBooking.class, booking.getId())
                        .orElseThrow()
                        .getBookedBy());
    }

    @Test
    void bookingsMadeByAQuittingLeaderPassToTheReplacement() throws Exception {
        var leader = login("S001");
        BookedGroup booked = groupWithBookedRoom(leader, leader);
        var booking =
                repository.all(studybuddy.backend.venue.model.RoomBooking.class).stream()
                        .filter(b -> b.getSessionId().equals(booked.session()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("S001", booking.getBookedBy());
        call(leader, post("/api/groups/" + booked.group() + "/leader-quits?replacementLeaderId=S004"), 200);
        assertEquals(
                "S004",
                repository
                        .find(studybuddy.backend.venue.model.RoomBooking.class, booking.getId())
                        .orElseThrow()
                        .getBookedBy());
    }

    @Test
    void bookingsMadeByADeletedAccountPassToTheLeader() throws Exception {
        var leader = login("S001");
        var admin = login("ADMIN001");
        BookedGroup booked = groupWithBookedRoom(leader, login("S004"));
        var booking =
                repository.all(studybuddy.backend.venue.model.RoomBooking.class).stream()
                        .filter(b -> b.getSessionId().equals(booked.session()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("S004", booking.getBookedBy());
        call(admin, delete("/api/admin/accounts/S004"), 200);
        var after =
                repository
                        .find(studybuddy.backend.venue.model.RoomBooking.class, booking.getId())
                        .orElseThrow();
        assertEquals("S001", after.getBookedBy());
        assertEquals(booked.room(), after.getRoomId());
    }

    @Test
    void leaderCanEditASessionAndRoomIsKeptOnlyWhileItStillFits() throws Exception {
        var leader = login("S001");
        var member = login("S004");
        BookedGroup booked = groupWithBookedRoom(leader, member);
        String url = "/api/sessions/" + booked.session();
        java.util.function.BiFunction<String, Integer, java.util.Map<String, Object>> body =
                (start, size) ->
                        java.util.Map.of(
                                "groupId", booked.group(),
                                "sessionDate", nextMonday(),
                                "startTime", start,
                                "endTime", start.equals("10:00") ? "11:00" : "12:00",
                                "maxGroupSize", size);
        call(member, put(url), body.apply("10:00", 3), 403);
        // Same slot and a size the room still seats: the booking stays.
        JsonNode kept = call(leader, put(url), body.apply("10:00", 3), 200);
        assertEquals(booked.room(), kept.get("bookedRoomId").asText());
        // Size below the current members is still rejected.
        call(leader, put(url), body.apply("10:00", 2), 400);
        // Moving the time releases the booking, so the old slot is free again.
        JsonNode moved = call(leader, put(url), body.apply("11:00", 3), 200);
        assertTrue(moved.get("bookedRoomId").isNull());
        assertEquals("11:00:00", moved.get("startTime").asText());
        assertTrue(
                repository.all(studybuddy.backend.venue.model.RoomBooking.class).stream()
                        .noneMatch(b -> b.getSessionId().equals(booked.session())));
        call(leader, post("/api/groups/" + booked.group() + "/close"), 200);
        call(leader, put(url), body.apply("11:00", 3), 404);
    }

    @Test
    void roomsCannotBeBookedForAClosedGroup() throws Exception {
        var leader = login("S001");
        String group = newGroup(leader, 3), session = planSession(leader, group, "10:00", "11:00", 2);
        // Sessions saved before closure cleanup existed can still belong to a closed group.
        var stored = repository.find(studybuddy.backend.session.model.StudySession.class, session).orElseThrow();
        var closed = repository.find(studybuddy.backend.group.model.StudyGroup.class, group).orElseThrow();
        closed.setStatus(studybuddy.backend.group.model.GroupStatus.CLOSED);
        repository.save(group, closed);
        repository.save(session, stored);
        call(leader, post("/api/venues/bookings?sessionId=" + session + "&roomId=R001"), 409);
        call(leader, get("/api/venues/recommendations?sessionId=" + session), 409);
    }

    @Test
    void sessionSizeCoversCurrentMembersWithinTheGroupLimit() throws Exception {
        var leader = login("S001");
        var b = login("S002");
        var c = login("S003");
        String group = newGroup(leader, 6);
        accept(leader, b, group);
        accept(leader, c, group);
        call(leader, post("/api/sessions"),
                java.util.Map.of("groupId", group, "sessionDate", nextMonday(),
                        "startTime", "10:00", "endTime", "11:00", "maxGroupSize", 2), 400);
        call(leader, post("/api/sessions"),
                java.util.Map.of("groupId", group, "sessionDate", nextMonday(),
                        "startTime", "10:00", "endTime", "11:00", "maxGroupSize", 7), 400);
        String session = planSession(leader, group, "10:00", "11:00", 3);
        accept(leader, login("S004"), group);
        accept(leader, login("S005"), group);
        // R003 has 4 seats; the group grew to 5 members after the session was planned.
        call(leader, post("/api/venues/bookings?sessionId=" + session + "&roomId=R003"), 409);
        for (JsonNode room : call(leader, get("/api/venues/recommendations?sessionId=" + session), 200))
            assertTrue(room.get("capacity").asInt() >= 5);
    }

    @Test
    void unknownRoutesAndUnsupportedMethodsAreClientErrors() throws Exception {
        var student = login("S001");
        call(student, get("/api/does-not-exist"), 404);
        call(student, delete("/api/students"), 405);
    }

    @Test
    void corsAllowsLocalFrontendButRejectsOtherOrigins() throws Exception {
        mvc.perform(
                        post("/api/session/login")
                                .header("Origin", "http://127.0.0.1:4200")
                                .header("X-StudyBuddy-Request", "1")
                                .contentType("application/json")
                                .content("{\"idToken\":\"S001\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4200"));
        mvc.perform(
                        post("/api/session/login")
                                .header("Origin", "https://untrusted.example")
                                .header("X-StudyBuddy-Request", "1")
                                .contentType("application/json")
                                .content("{\"idToken\":\"S001\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentAcceptancesCannotOverfillGroup() throws Exception {
        var leader = login("S001");
        var b = login("S002");
        var c = login("S003");
        String group = newGroup(leader, 2), first = join(b, group), second = join(c, group);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var tasks =
                    java.util.stream.Stream.of(first, second)
                            .map(
                                    id ->
                                            executor.submit(
                                                    () -> {
                                                        start.await();
                                                        return mvc.perform(
                                                                        post("/api/groups/join-requests/"
                                                                                        + id
                                                                                        + "/decision?decision=ACCEPTED")
                                                                                .session(leader)
                                                                                .header(
                                                                                        "X-StudyBuddy-Request",
                                                                                        "1"))
                                                                .andReturn()
                                                                .getResponse()
                                                                .getStatus();
                                                    }))
                            .toList();
            start.countDown();
            var codes =
                    List.of(
                            tasks.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                            tasks.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(codes.contains(200));
            assertTrue(codes.contains(409));
            assertEquals(
                    2,
                    repository
                            .find(studybuddy.backend.group.model.StudyGroup.class, group)
                            .orElseThrow()
                            .getMemberIds()
                            .size());
        } finally {
            executor.shutdownNow();
            call(leader, post("/api/groups/" + group + "/close"), 200);
        }
    }
}
