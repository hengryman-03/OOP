package studybuddy.backend.auth;

import com.google.firebase.auth.FirebaseToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.persistence.StudyRepository;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final SessionService sessions;
    private final StudyRepository repository;
    private final FirebaseConfig firebaseConfig;

    public SessionController(
            SessionService sessions,
            StudyRepository repository,
            FirebaseConfig firebaseConfig) {
        this.sessions = sessions;
        this.repository = repository;
        this.firebaseConfig = firebaseConfig;
    }

    public record TokenLogin(@NotBlank String idToken) {}

    public record SignUp(@NotBlank String idToken, @NotBlank String name) {}

    /** Login: verify Firebase ID token and establish a session. */
    @PostMapping("/login")
    public UserAccount login(@Valid @RequestBody TokenLogin body, HttpServletRequest request) {
        FirebaseToken token = firebaseConfig.verifyIdToken(body.idToken());
        String uid = token.getUid();
        UserAccount account =
                repository
                        .find(UserAccount.class, uid)
                        .orElseThrow(() -> DomainException.missing("No account found for this user."));
        if (!"ACTIVE".equals(account.getStatus()))
            throw DomainException.forbidden("This account is suspended.");
        HttpSession session = request.getSession();
        session.setAttribute("accountId", uid);
        request.changeSessionId();
        return account;
    }

    /** Sign up: verify Firebase ID token, create a new student account, and establish a session. */
    @PostMapping("/signup")
    public UserAccount signup(@Valid @RequestBody SignUp body, HttpServletRequest request) {
        FirebaseToken token = firebaseConfig.verifyIdToken(body.idToken());
        String uid = token.getUid();
        if (repository.find(UserAccount.class, uid).isPresent())
            throw new DomainException(
                    org.springframework.http.HttpStatus.CONFLICT, "Account already exists.");
        UserAccount account = new UserAccount(uid, body.name(), "STUDENT", "ACTIVE");
        account.setCreatedAt(Instant.now());
        repository.save(uid, account);
        HttpSession session = request.getSession();
        session.setAttribute("accountId", uid);
        request.changeSessionId();
        return account;
    }

    /** Returns the currently logged-in account. */
    @GetMapping
    public UserAccount current(HttpSession session) {
        return sessions.account(session);
    }

    /** Logout: invalidate the session. */
    @DeleteMapping
    public Map<String, Boolean> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true);
    }
}
