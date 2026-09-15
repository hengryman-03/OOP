package studybuddy.backend.group.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.common.StudyValidation;
import studybuddy.backend.connection.model.RequestStatus;
import studybuddy.backend.group.model.*;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.service.StudentService;

import java.util.List;
import java.util.UUID;

@Service
public class StudyGroupService {
    private final StudyRepository repository;
    private final StudentService students;

    public StudyGroupService(StudyRepository repository, StudentService students) {
        this.repository = repository;
        this.students = students;
    }

    public List<StudyGroup> listGroups() {
        return repository.all(StudyGroup.class);
    }

    public StudyGroup required(String id) {
        return repository
                .find(StudyGroup.class, id)
                .orElseThrow(() -> DomainException.missing("Study group not found."));
    }

    private void requireManager(StudyGroup group, Actor actor) {
        if (!actor.isAdmin() && !group.getLeaderId().equals(actor.id()))
            throw DomainException.forbidden(
                    "Only the group leader or an administrator can manage this group.");
    }

    private void requireOpen(StudyGroup group) {
        if (group.getStatus() == GroupStatus.CLOSED)
            throw DomainException.conflict("This group is closed.");
    }

    private void validate(StudyGroup group) {
        students.requireCourse(group.getCourseCode());
        StudyValidation.slots(group.getAvailability());
    }

    @Transactional
    public StudyGroup createGroup(StudyGroup group, Actor actor) {
        repository.lock();
        actor.requireStudent();
        students.required(actor.id());
        validate(group);
        group.setId(UUID.randomUUID().toString());
        group.setLeaderId(actor.id());
        group.setMemberIds(List.of(actor.id()));
        group.setStatus(GroupStatus.ACTIVE);
        return repository.save(group.getId(), group);
    }

    @Transactional
    public StudyGroup updateGroup(String id, StudyGroup updated, Actor actor) {
        repository.lock();
        StudyGroup group = required(id);
        requireManager(group, actor);
        requireOpen(group);
        validate(updated);
        if (updated.getMaximumGroupSize() < group.getMemberIds().size())
            throw DomainException.conflict("Capacity cannot be smaller than current membership.");
        updated.setId(id);
        updated.setLeaderId(group.getLeaderId());
        updated.setMemberIds(group.getMemberIds());
        updated.setStatus(group.getStatus());
        return repository.save(id, updated);
    }

    public List<MembershipRequest> requests(Actor actor) {
        var managed =
                listGroups().stream()
                        .filter(g -> actor.isAdmin() || g.getLeaderId().equals(actor.id()))
                        .map(StudyGroup::getId)
                        .toList();
        return repository.all(MembershipRequest.class).stream()
                .filter(
                        r ->
                                r.getStudentId().equals(actor.id())
                                        || managed.contains(r.getGroupId()))
                .sorted(java.util.Comparator.comparing(MembershipRequest::getCreatedAt).reversed())
                .toList();
    }

    @Transactional
    public MembershipRequest requestToJoin(String id, Actor actor) {
        repository.lock();
        actor.requireStudent();
        students.required(actor.id());
        StudyGroup group = required(id);
        requireOpen(group);
        if (group.getMemberIds().contains(actor.id()))
            throw DomainException.conflict("You are already a member.");
        if (group.getMemberIds().size() >= group.getMaximumGroupSize())
            throw DomainException.conflict("This group is full.");
        if (repository.all(MembershipRequest.class).stream()
                .anyMatch(
                        r ->
                                r.getGroupId().equals(id)
                                        && r.getStudentId().equals(actor.id())
                                        && r.getStatus() == RequestStatus.PENDING))
            throw DomainException.conflict("You already have a pending request for this group.");
        MembershipRequest request =
                new MembershipRequest(UUID.randomUUID().toString(), id, actor.id());
        return repository.save(request.getId(), request);
    }

    @Transactional
    public MembershipRequest decideMembership(String id, RequestStatus decision, Actor actor) {
        repository.lock();
        MembershipRequest request =
                repository
                        .find(MembershipRequest.class, id)
                        .orElseThrow(
                                () -> DomainException.missing("Membership request not found."));
        StudyGroup group = required(request.getGroupId());
        requireManager(group, actor);
        requireOpen(group);
        if (request.getStatus() != RequestStatus.PENDING)
            throw DomainException.conflict("This membership request has already been decided.");
        if (decision != RequestStatus.ACCEPTED && decision != RequestStatus.DECLINED)
            throw new IllegalArgumentException("Accept or decline the membership request.");
        if (decision == RequestStatus.ACCEPTED) {
            students.requireActiveStudent(request.getStudentId());
            if (group.getMemberIds().size() >= group.getMaximumGroupSize())
                throw DomainException.conflict("This group is full.");
            if (group.getMemberIds().contains(request.getStudentId()))
                throw DomainException.conflict("Student is already a member.");
            group.getMemberIds().add(request.getStudentId());
            repository.save(group.getId(), group);
        }
        request.setStatus(decision);
        return repository.save(id, request);
    }

    @Transactional
    public StudyGroup removeMember(String id, String studentId, Actor actor) {
        repository.lock();
        StudyGroup group = required(id);
        requireOpen(group);
        if (!actor.id().equals(studentId)) requireManager(group, actor);
        if (studentId.equals(group.getLeaderId()))
            throw DomainException.conflict(
                    "The leader must transfer leadership or close the group before leaving.");
        if (!group.getMemberIds().remove(studentId))
            throw DomainException.missing("Student is not a group member.");
        return repository.save(id, group);
    }

    private void transfer(StudyGroup group, String replacement) {
        if (replacement == null
                || replacement.equals(group.getLeaderId())
                || !group.getMemberIds().contains(replacement))
            throw new IllegalArgumentException("Choose another existing member as the new leader.");
        students.requireActiveStudent(replacement);
        group.setLeaderId(replacement);
    }

    @Transactional
    public StudyGroup transferLeadership(String id, String newLeaderId, Actor actor) {
        repository.lock();
        StudyGroup group = required(id);
        requireManager(group, actor);
        requireOpen(group);
        transfer(group, newLeaderId);
        return repository.save(id, group);
    }

    private void close(StudyGroup group) {
        group.setStatus(GroupStatus.CLOSED);
        repository.all(MembershipRequest.class).stream()
                .filter(
                        r ->
                                r.getGroupId().equals(group.getId())
                                        && r.getStatus() == RequestStatus.PENDING)
                .forEach(
                        r -> {
                            r.setStatus(RequestStatus.DECLINED);
                            repository.save(r.getId(), r);
                        });
    }

    @Transactional
    public StudyGroup closeGroup(String id, Actor actor) {
        repository.lock();
        StudyGroup group = required(id);
        requireManager(group, actor);
        close(group);
        return repository.save(id, group);
    }

    @Transactional
    public StudyGroup leaderQuits(String id, String replacement, boolean closeGroup, Actor actor) {
        repository.lock();
        StudyGroup group = required(id);
        actor.requireSelf(group.getLeaderId());
        requireOpen(group);
        String oldLeader = group.getLeaderId();
        if (closeGroup || group.getMemberIds().size() <= 1) {
            close(group);
            group.getMemberIds().remove(oldLeader);
        } else {
            if (replacement == null || replacement.isBlank())
                throw DomainException.conflict(
                        "Choose a replacement leader or close the group before leaving.");
            transfer(group, replacement);
            group.getMemberIds().remove(oldLeader);
        }
        return repository.save(id, group);
    }
}
