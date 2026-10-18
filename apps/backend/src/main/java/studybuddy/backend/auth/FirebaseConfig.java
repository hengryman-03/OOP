package studybuddy.backend.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;

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
            return FirebaseAuth.getInstance().verifyIdToken(idToken, true).getUid();
        } catch (FirebaseAuthException e) {
            throw DomainException.forbidden("Invalid or expired Firebase token.");
        }
    }

    /** Create a passwordless identity; the browser sends the Firebase password-set email. */
    public String createStudent(String name, String email) {
        try {
            return FirebaseAuth.getInstance()
                    .createUser(new UserRecord.CreateRequest().setEmail(email).setDisplayName(name))
                    .getUid();
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS)
                throw DomainException.conflict("This email already has a Firebase account.");
            throw new DomainException(
                    HttpStatus.BAD_GATEWAY, "Firebase could not create the student account.");
        }
    }

    /** Missing users include seeded demo accounts and safe retries after partial deletion. */
    public void deleteUser(String uid) {
        try {
            FirebaseAuth.getInstance().deleteUser(uid);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) return;
            throw new DomainException(
                    HttpStatus.BAD_GATEWAY, "Firebase could not delete the student identity.");
        }
    }
}
