package studybuddy.backend.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.auth.FirebaseConfig;
import studybuddy.backend.group.model.StudyGroup;
import studybuddy.backend.student.model.Course;
import studybuddy.backend.student.model.StudentProfile;
import studybuddy.backend.venue.model.Room;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore stores each record as a document map. Every seeded record must survive record →
 * document → record unchanged, including Firestore returning whole numbers as {@code Long}.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studybuddy-tests;DB_CLOSE_DELAY=-1")
class FirestoreDocumentMappingTest {
    private static final TypeReference<Map<String, Object>> DOCUMENT = new TypeReference<>() {};

    @Autowired StudyRepository repository;
    @Autowired ObjectMapper json;
    @MockitoBean FirebaseConfig firebaseConfig;

    @Test
    void seededRecordsSurviveTheDocumentRoundTrip() throws Exception {
        for (Class<?> type :
                List.of(
                        UserAccount.class,
                        StudentProfile.class,
                        Course.class,
                        StudyGroup.class,
                        Room.class,
                        DemoDataInitializer.SeedMarker.class)) {
            List<?> records = repository.all(type);
            assertFalse(records.isEmpty(), type.getSimpleName() + " should be seeded");
            for (Object record : records) {
                Map<String, Object> document = json.convertValue(record, DOCUMENT);
                Object back = json.convertValue(asFirestoreReturnsIt(document), type);
                assertEquals(
                        json.writeValueAsString(record),
                        json.writeValueAsString(back),
                        type.getSimpleName());
            }
        }
    }

    /** Firestore keeps every integer as a 64-bit number, so Integer values come back as Long. */
    private static Object asFirestoreReturnsIt(Object value) {
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put((String) k, asFirestoreReturnsIt(v)));
            return copy;
        }
        if (value instanceof List<?> list)
            return list.stream().map(FirestoreDocumentMappingTest::asFirestoreReturnsIt).toList();
        return value;
    }
}
