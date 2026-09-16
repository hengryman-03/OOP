package studybuddy.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.matching.service.AvailabilityCalculator;
import studybuddy.backend.matching.service.MatchingService;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.model.AvailabilitySlot;
import studybuddy.backend.student.model.StudentProfile;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** HTTP integration tests use separate sessions and real H2 transactions, not mocked services. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studybuddy-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class StudyBuddyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StudyRepository repository;
    @Autowired MatchingService matching;

    MockHttpSession login(String id) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/session")
                                        .header("X-StudyBuddy-Request", "1")
                                        .contentType("application/json")
                                        .content("{\"accountId\":\"" + id + "\"}"))
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
                        post("/api/session")
                                .contentType("application/json")
                                .content("{\"accountId\":\"S001\"}"))
                .andExpect(status().isForbidden());
        var student = login("S001");
        assertEquals(50, call(student, get("/api/students"), 200).size());
        assertEquals(10, call(student, get("/api/students/courses"), 200).size());
        call(student, get("/api/admin/accounts"), 403);
        call(student, get("/api/admin/matching-config"), 403);
        call(student, get("/api/connections/students/S002"), 403);
        call(student, get("/api/students/S002/matches"), 403);
        assertEquals(51, call(login("ADMIN001"), get("/api/admin/accounts"), 200).size());
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
        assertNotEquals(before, call(student, get("/api/students/S001/matches"), 200).toString());
        assertEquals(
                "AVAILABILITY_FIRST",
                repository.find(MatchingProperties.class, "current").orElseThrow().getStrategy());
        config.put("strategy", "COURSE_FIRST");
        call(admin, put("/api/admin/matching-config"), config, 200);
        assertEquals(
                100,
                java.util.Arrays.stream(matching.effectiveWeights(matching.getCurrentConfig()))
                        .sum());
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
    void accountCreationProfileSetupSuspensionReactivationAndDeletion() throws Exception {
        var admin = login("ADMIN001");
        JsonNode created =
                call(
                        admin,
                        post("/api/admin/accounts"),
                        java.util.Map.of(
                                "name", "New learner", "role", "STUDENT", "status", "ACTIVE"),
                        200);
        String id = created.get("id").asText();
        var student = login(id);
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
        call(student, get("/api/students"), 401);
        assertTrue(repository.find(StudentProfile.class, id).isEmpty());
        assertEquals(0, call(login("S004"), get("/api/connections/students/S004"), 200).size());
    }

    @Test
    void corsAllowsLocalFrontendButRejectsOtherOrigins() throws Exception {
        mvc.perform(
                        post("/api/session")
                                .header("Origin", "http://127.0.0.1:4200")
                                .header("X-StudyBuddy-Request", "1")
                                .contentType("application/json")
                                .content("{\"accountId\":\"S001\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:4200"));
        mvc.perform(
                        post("/api/session")
                                .header("Origin", "https://untrusted.example")
                                .header("X-StudyBuddy-Request", "1")
                                .contentType("application/json")
                                .content("{\"accountId\":\"S001\"}"))
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
