package studybuddy.backend.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.connection.model.*;
import studybuddy.backend.group.model.*;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.model.StudentProfile;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final StudyRepository repository;

    public AdminService(StudyRepository repository) {
        this.repository = repository;
    }

    public record AccountUsage(
            UserAccount account,
            boolean profileComplete,
            long activeConnections,
            long groupsJoined,
            long pendingRequests) {}

    public List<AccountUsage> listAccounts(Actor actor) {
        actor.requireAdmin();
        var requests = repository.all(BuddyRequest.class);
        var groups = repository.all(StudyGroup.class);
        return repository.all(UserAccount.class).stream()
                .map(
                        a ->
                                new AccountUsage(
                                        a,
                                        repository
                                                .find(StudentProfile.class, a.getId())
                                                .isPresent(),
                                        requests.stream()
                                                .filter(
                                                        r ->
                                                                involved(r, a.getId())
                                                                        && r.getStatus()
                                                                                == RequestStatus
                                                                                        .ACCEPTED)
                                                .count(),
                                        groups.stream()
                                                .filter(
                                                        g ->
                                                                g.getStatus() == GroupStatus.ACTIVE
                                                                        && g.getMemberIds()
                                                                                .contains(
                                                                                        a.getId()))
                                                .count(),
                                        requests.stream()
                                                .filter(
                                                        r ->
                                                                involved(r, a.getId())
                                                                        && r.getStatus()
                                                                                == RequestStatus
                                                                                        .PENDING)
                                                .count()))
                .toList();
    }

    private boolean involved(BuddyRequest r, String id) {
        return id.equals(r.getSenderId()) || id.equals(r.getReceiverId());
    }

    @Transactional
    public UserAccount saveAccount(UserAccount input, Actor actor) {
        repository.lock();
        actor.requireAdmin();
        UserAccount current =
                input.getId() == null || input.getId().isBlank()
                        ? null
                        : repository
                                .find(UserAccount.class, input.getId())
                                .orElseThrow(() -> DomainException.missing("Account not found."));
        if (current == null)
            input.setId(("STUDENT".equals(input.getRole()) ? "S-" : "ADMIN-") + UUID.randomUUID());
        else {
            if (!current.getRole().equals(input.getRole()))
                throw new IllegalArgumentException(
                        "Account roles cannot be changed; create an account for the required"
                            + " role.");
            if (actor.id().equals(current.getId()) && !"ACTIVE".equals(input.getStatus()))
                throw DomainException.conflict(
                        "You cannot suspend your own administrator account.");
            if (!"ACTIVE".equals(input.getStatus())) ensureNoActiveLeadership(input.getId());
            input.setCreatedAt(current.getCreatedAt());
            input.setLastActiveAt(current.getLastActiveAt());
            repository
                    .find(StudentProfile.class, input.getId())
                    .ifPresent(
                            profile -> {
                                profile.setName(input.getName());
                                repository.save(profile.getId(), profile);
                            });
        }
        return repository.save(input.getId(), input);
    }

    private void ensureNoActiveLeadership(String id) {
        if (repository.all(StudyGroup.class).stream()
                .anyMatch(g -> g.getStatus() == GroupStatus.ACTIVE && id.equals(g.getLeaderId())))
            throw DomainException.conflict(
                    "Transfer leadership or close this student's active groups before suspending or"
                        + " deleting the account.");
    }

    @Transactional
    public void deleteAccount(String id, Actor actor) {
        repository.lock();
        actor.requireAdmin();
        if (id.equals(actor.id()))
            throw DomainException.conflict("You cannot delete your own administrator account.");
        repository
                .find(UserAccount.class, id)
                .orElseThrow(() -> DomainException.missing("Account not found."));
        ensureNoActiveLeadership(id);
        // Delete personal records together so no request or profile survives its account.
        repository.all(BuddyRequest.class).stream()
                .filter(r -> involved(r, id))
                .forEach(r -> repository.delete(BuddyRequest.class, r.getId()));
        repository.all(MembershipRequest.class).stream()
                .filter(r -> id.equals(r.getStudentId()))
                .forEach(r -> repository.delete(MembershipRequest.class, r.getId()));
        repository.all(StudyGroup.class).stream()
                .filter(g -> g.getMemberIds().contains(id))
                .forEach(
                        g -> {
                            g.getMemberIds().remove(id);
                            repository.save(g.getId(), g);
                        });
        repository.delete(StudentProfile.class, id);
        repository.delete(UserAccount.class, id);
    }
}
