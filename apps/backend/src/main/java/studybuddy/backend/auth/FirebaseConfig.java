package studybuddy.backend.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;

import studybuddy.backend.common.DomainException;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.service-account}")
    private String serviceAccountLocation;

    @Value("${app.firebase.database-url}")
    private String databaseUrl;

    @PostConstruct
    public void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            // An unprefixed path must resolve on disk, not relative to the classpath.
            Resource serviceAccountResource = serviceAccountLocation.startsWith("classpath:")
                            || serviceAccountLocation.startsWith("file:")
                    ? new DefaultResourceLoader().getResource(serviceAccountLocation)
                    : new FileSystemResource(serviceAccountLocation);
            if (!serviceAccountResource.exists()) {
                throw new IllegalStateException(
                        "Firebase Admin credential was not found. Set FIREBASE_SERVICE_ACCOUNT to the absolute path of a service-account JSON file.");
            }
            try (InputStream credentials = serviceAccountResource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentials))
                        .setDatabaseUrl(databaseUrl)
                        .build();
                FirebaseApp.initializeApp(options);
            }
        }
    }

    /** The controller only needs the verified UID; keep Firebase token objects inside this adapter. */
    public String verifiedUid(String idToken) {
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idToken).getUid();
        } catch (FirebaseAuthException e) {
            throw DomainException.forbidden("Invalid or expired Firebase token.");
        }
    }
}
