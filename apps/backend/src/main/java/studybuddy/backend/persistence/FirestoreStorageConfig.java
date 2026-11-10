package studybuddy.backend.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import studybuddy.backend.auth.FirebaseConfig;

/**
 * Wiring for {@code APP_STORAGE=firestore}. The {@code firestore} profile
 * (application-firestore.properties) also switches off the H2 data source, so this mode never
 * opens or locks the local database file.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage", havingValue = "firestore")
public class FirestoreStorageConfig {

    /** Reuses the Firebase Admin app and credentials that sign-in already uses. */
    @Bean
    Firestore firestore(FirebaseConfig firebaseConfig) {
        // The parameter makes Spring initialise FirebaseConfig (and FirebaseApp) first.
        return FirestoreClient.getFirestore(FirebaseApp.getInstance());
    }

    @Bean
    FirestoreMirror firestoreMirror(
            Firestore firestore,
            @Value("${app.firestore.collection-prefix:}") String collectionPrefix) {
        return new FirestoreMirror(firestore, collectionPrefix);
    }

    @Bean
    StudyRepository studyRepository(
            Firestore firestore,
            FirestoreMirror mirror,
            ObjectMapper json,
            @Value("${app.firestore.collection-prefix:}") String collectionPrefix) {
        return new FirestoreStudyRepository(firestore, mirror, json, collectionPrefix);
    }

    /** Named transactionManager so every existing {@code @Transactional} method uses it. */
    @Bean
    PlatformTransactionManager transactionManager() {
        return new InProcessTransactionManager();
    }
}
