package studybuddy.backend.auth;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.persistence.StudyRepository;

@Service
public class SessionService {
    private static final String CURRENT_ACCOUNT = SessionService.class.getName() + ".account";

    private final StudyRepository repository;

    public SessionService(StudyRepository repository) {
        this.repository = repository;
    }

    public UserAccount account(HttpSession session) {
        // ApiSessionInterceptor and the controller both check the account on every request; reuse
        // the first lookup, since each one is a Firestore round trip.
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null
                && request.getAttribute(CURRENT_ACCOUNT, RequestAttributes.SCOPE_REQUEST)
                        instanceof UserAccount cached) return cached;
        Object id = session.getAttribute("accountId");
        UserAccount account =
                repository
                        .find(UserAccount.class, id == null ? "" : id.toString())
                        .orElseThrow(
                                () -> new DomainException(HttpStatus.UNAUTHORIZED, "Not logged in."));
        if (!"ACTIVE".equals(account.getStatus()))
            throw DomainException.forbidden("This account is suspended.");
        if (request != null)
            request.setAttribute(CURRENT_ACCOUNT, account, RequestAttributes.SCOPE_REQUEST);
        return account;
    }

    public Actor actor(HttpSession session) {
        return Actor.from(account(session));
    }
}
