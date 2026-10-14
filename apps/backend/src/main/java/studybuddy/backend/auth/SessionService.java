package studybuddy.backend.auth;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.persistence.StudyRepository;

@Service
public class SessionService {
    private final StudyRepository repository;

    public SessionService(StudyRepository repository) {
        this.repository = repository;
    }

    public UserAccount account(HttpSession session) {
        Object id = session.getAttribute("accountId");
        UserAccount account =
                repository
                        .find(UserAccount.class, id == null ? "" : id.toString())
                        .orElseThrow(
                                () -> new DomainException(HttpStatus.UNAUTHORIZED, "Not logged in."));
        if (!"ACTIVE".equals(account.getStatus()))
            throw DomainException.forbidden("This account is suspended.");
        return account;
    }

    public Actor actor(HttpSession session) {
        return Actor.from(account(session));
    }
}
