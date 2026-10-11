package studybuddy.backend.session.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.group.model.GroupStatus;
import studybuddy.backend.group.model.StudyGroup;
import studybuddy.backend.group.service.StudyGroupService;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.session.model.StudySession;
import studybuddy.backend.venue.model.RoomBooking;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class StudySessionService {
    private final StudyRepository repository;
    private final StudyGroupService groups;

    public StudySessionService(StudyRepository repository, StudyGroupService groups) {
        this.repository = repository;
        this.groups = groups;
    }

    public StudySession required(String id) {
        return repository
                .find(StudySession.class, id)
                .orElseThrow(() -> DomainException.missing("Study session not found."));
    }

    private void requireManager(StudyGroup group, Actor actor) {
        if (!actor.isAdmin() && !group.getLeaderId().equals(actor.id()))
            throw DomainException.forbidden(
                    "Only the group leader or an administrator can plan sessions for this group.");
    }

    private void requireParticipant(StudyGroup group, Actor actor) {
        if (!actor.isAdmin()
                && !group.getLeaderId().equals(actor.id())
                && !group.getMemberIds().contains(actor.id()))
            throw DomainException.forbidden("You must be a member of this group.");
    }

    private void validate(StudySession session) {
        if (session.getSessionDate().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Choose a session date that hasn't passed yet.");
        if (!session.getStartTime().isBefore(session.getEndTime())
                || session.getStartTime().getSecond() != 0
                || session.getEndTime().getSecond() != 0)
            throw new IllegalArgumentException(
                    "The session needs an end time after its start, in whole minutes.");
    }

    @Transactional
    public StudySession createSession(StudySession session, Actor actor) {
        repository.lock();
        actor.requireStudent();
        StudyGroup group = groups.required(session.getGroupId());
        requireManager(group, actor);
        if (group.getStatus() == GroupStatus.CLOSED)
            throw DomainException.conflict("This group is closed.");
        validate(session);
        session.setId(UUID.randomUUID().toString());
        session.setPlannedBy(actor.id());
        session.setBookedRoomId(null);
        return repository.save(session.getId(), session);
    }

    public List<StudySession> forGroup(String groupId, Actor actor) {
        StudyGroup group = groups.required(groupId);
        requireParticipant(group, actor);
        return repository.all(StudySession.class).stream()
                .filter(s -> s.getGroupId().equals(groupId))
                .sorted(
                        Comparator.comparing(StudySession::getSessionDate)
                                .thenComparing(StudySession::getStartTime))
                .toList();
    }

    /** Every session across every group the actor leads or belongs to, for the Venue page's picker. */
    public List<StudySession> bookable(Actor actor) {
        var myGroupIds =
                groups.listGroups().stream()
                        .filter(
                                g ->
                                        g.getStatus() == GroupStatus.ACTIVE
                                                && (g.getLeaderId().equals(actor.id())
                                                        || g.getMemberIds().contains(actor.id())))
                        .map(StudyGroup::getId)
                        .toList();
        return repository.all(StudySession.class).stream()
                .filter(s -> myGroupIds.contains(s.getGroupId()))
                .sorted(
                        Comparator.comparing(StudySession::getSessionDate)
                                .thenComparing(StudySession::getStartTime))
                .toList();
    }

    @Transactional
    public void cancelSession(String id, Actor actor) {
        repository.lock();
        StudySession session = required(id);
        StudyGroup group = groups.required(session.getGroupId());
        requireManager(group, actor);
        repository.all(RoomBooking.class).stream()
                .filter(b -> b.getSessionId().equals(id))
                .forEach(b -> repository.delete(RoomBooking.class, b.getId()));
        repository.delete(StudySession.class, id);
    }
}
