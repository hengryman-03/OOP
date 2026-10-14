package studybuddy.backend.auth;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.common.DomainException;

/** Trusted identity is resolved from the server session, never from form ownership IDs. */
public record Actor(String id, String role) {
    public static Actor from(UserAccount account) {
        return new Actor(account.getId(), account.getRole());
    }

    public boolean isAdmin() {
        return "SYSTEM_ADMINISTRATOR".equals(role);
    }

    public void requireAdmin() {
        if (!isAdmin()) throw DomainException.forbidden("Administrator access required.");
    }

    public void requireStudent() {
        if (!"STUDENT".equals(role))
            throw DomainException.forbidden("Switch to a student account for this action.");
    }

    public void requireSelf(String studentId) {
        if (!id.equals(studentId))
            throw DomainException.forbidden("You can only manage your own student activity.");
    }
}
