package studybuddy.backend.student.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.DomainException;
import studybuddy.backend.common.StudyValidation;
import studybuddy.backend.connection.model.BuddyRequest;
import studybuddy.backend.connection.model.RequestStatus;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.model.*;

import java.util.List;
import java.util.Optional;

@Service
public class StudentService {
    private final StudyRepository repository;

    public StudentService(StudyRepository repository) {
        this.repository = repository;
    }

    public List<StudentProfile> findAll() {
        var activeIds =
                repository.all(UserAccount.class).stream()
                        .filter(a -> "ACTIVE".equals(a.getStatus()))
                        .map(UserAccount::getId)
                        .collect(java.util.stream.Collectors.toSet());
        return repository.all(StudentProfile.class).stream()
                .filter(s -> activeIds.contains(s.getId()))
                .toList();
    }

    public Optional<StudentProfile> findById(String id) {
        return repository.find(StudentProfile.class, id);
    }

    public StudentProfile required(String id) {
        return findById(id)
                .orElseThrow(() -> DomainException.missing("Student profile not found."));
    }

    public void requireActiveStudent(String id) {
        UserAccount account =
                repository
                        .find(UserAccount.class, id)
                        .orElseThrow(() -> DomainException.missing("Student account not found."));
        if (!"STUDENT".equals(account.getRole()) || !"ACTIVE".equals(account.getStatus()))
            throw DomainException.conflict("Student account is not active.");
    }

    public List<Course> findCourses() {
        return repository.all(Course.class);
    }

    public void requireCourse(String code) {
        if (code == null || repository.find(Course.class, code).isEmpty())
            throw new IllegalArgumentException("Select a valid course.");
    }

    public void validate(StudentProfile profile) {
        StudyValidation.slots(profile.getPreference().getAvailability());
        profile.getCurrentCourses().forEach(this::requireCourse);
        requireCourse(profile.getPreference().getCourseCode());
        if (!profile.getCurrentCourses().contains(profile.getPreference().getCourseCode()))
            throw new IllegalArgumentException(
                    "Your preferred course must be one of your current courses.");
        if (profile.getPreference().getPreferredArrangement() == StudyArrangement.ONE_TO_ONE
                && profile.getPreference().getPreferredGroupSize() != 2)
            throw new IllegalArgumentException(
                    "One-to-one study requires a preferred group size of 2.");
    }

    @Transactional
    public StudentProfile save(StudentProfile profile, Actor actor) {
        repository.lock();
        actor.requireStudent();
        if (profile.getId() != null && !profile.getId().equals(actor.id()))
            actor.requireSelf(profile.getId());
        validate(profile);
        profile.setId(actor.id());
        profile.setCurrentCourses(profile.getCurrentCourses().stream().distinct().toList());
        UserAccount account = repository.find(UserAccount.class, actor.id()).orElseThrow();
        account.setName(profile.getName());
        repository.save(account.getId(), account);
        return repository.save(profile.getId(), profile);
    }

    public boolean connected(String a, String b) {
        return repository.all(BuddyRequest.class).stream()
                .anyMatch(
                        r ->
                                r.getStatus() == RequestStatus.ACCEPTED
                                        && ((r.getSenderId().equals(a)
                                                        && r.getReceiverId().equals(b))
                                                || (r.getSenderId().equals(b)
                                                        && r.getReceiverId().equals(a))));
    }

    public StudentProfile publicProfile(String id, Actor viewer) {
        return required(id).toPublicProfile(viewer.id().equals(id) || connected(viewer.id(), id));
    }
}
