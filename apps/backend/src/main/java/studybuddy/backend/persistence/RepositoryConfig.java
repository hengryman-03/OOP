package studybuddy.backend.persistence;

import com.google.firebase.cloud.FirestoreClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studybuddy.backend.auth.FirebaseConfig;

/**
 * Chooses the {@link StudyRepository} implementation from {@code app.repository}: {@code
 * firestore} (default, shared cloud data) or {@code memory} (throwaway data for tests).
 */
@Configuration
public class RepositoryConfig {

    // FirebaseConfig is a parameter so the Firebase app is initialized before Firestore is used.
    @Bean
    @ConditionalOnProperty(name = "app.repository", havingValue = "firestore", matchIfMissing = true)
    public StudyRepository firestoreStudyRepository(FirebaseConfig firebase, DocumentCodec codec) {
        return new FirestoreStudyRepository(FirestoreClient.getFirestore(), codec);
    }

    @Bean
    @ConditionalOnProperty(name = "app.repository", havingValue = "memory")
    public StudyRepository inMemoryStudyRepository(DocumentCodec codec) {
        return new InMemoryStudyRepository(codec);
    }
}
