package studybuddy.backend.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.group.model.*;
import studybuddy.backend.student.model.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** Deterministic fictional data is installed once, never restored after account deletion. */
@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final StudyRepository repository;

    public DemoDataInitializer(StudyRepository repository) {
        this.repository = repository;
    }

    public record SeedMarker(boolean initialized) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.lock();
        if (repository.find(SeedMarker.class, "v1").isPresent()) return;
        List<Course> courses =
                List.of(
                        new Course("IS442", "Object Oriented Programming"),
                        new Course("IS111", "Introduction to Programming"),
                        new Course("IS112", "Data Management"),
                        new Course("IS113", "Web Application Development"),
                        new Course("IS114", "Computing Foundations"),
                        new Course("IS212", "Software Project Management"),
                        new Course("IS213", "Enterprise Solution Development"),
                        new Course("IS216", "Web Application Development II"),
                        new Course("CS101", "Programming Fundamentals"),
                        new Course("STAT101", "Introductory Statistics"));
        courses.forEach(c -> repository.save(c.getCode(), c));
        repository.save(
                "ADMIN001",
                new UserAccount(
                        "ADMIN001", "Demo Administrator", "SYSTEM_ADMINISTRATOR", "ACTIVE"));
        String[] first = {
            "Avery", "Jamie", "Morgan", "Riley", "Casey", "Jordan", "Taylor", "Alex", "Robin", "Sam"
        };
        String[] last = {"Tan", "Lim", "Lee", "Wong", "Goh"};
        for (int i = 0; i < 50; i++) {
            String id = "S" + String.format("%03d", i + 1),
                    name = first[i % 10] + " " + last[i / 10];
            String course = courses.get(i % courses.size()).getCode();
            List<String> enrolled =
                    java.util.stream.Stream.of(course, "IS442", courses.get((i + 3) % 10).getCode())
                            .distinct()
                            .toList();
            StudyArrangement arrangement = StudyArrangement.values()[i % 3];
            StudyPreference preference =
                    new StudyPreference(
                            course,
                            StudyMode.values()[i % 3],
                            arrangement,
                            StudyGoal.values()[i % 4],
                            arrangement == StudyArrangement.ONE_TO_ONE ? 2 : 3 + i % 3,
                            List.of(
                                    new AvailabilitySlot(
                                            DayOfWeek.WEDNESDAY,
                                            LocalTime.of(18 + i % 3, 0),
                                            LocalTime.of(21, 0)),
                                    new AvailabilitySlot(
                                            DayOfWeek.of(i % 5 + 1),
                                            LocalTime.of(14, 0),
                                            LocalTime.of(16, 0))));
            repository.save(id, new UserAccount(id, name, "STUDENT", "ACTIVE"));
            repository.save(
                    id,
                    new StudentProfile(
                            id,
                            name,
                            i % 4 == 0 ? "School of Computing and Information Systems" : "SCIS",
                            i % 2 == 0 ? "Information Systems" : "Computer Science",
                            1 + i % 4,
                            "9000" + String.format("%04d", i + 1),
                            enrolled,
                            preference));
        }
        for (int i = 0; i < 3; i++) {
            StudyGroup group = new StudyGroup();
            group.setId("G00" + (i + 1));
            group.setCourseCode(courses.get(i).getCode());
            group.setName(
                    new String[] {"OOP study circle", "Programming practice", "Data management lab"}
                            [i]);
            group.setDescription(
                    new String[] {
                                "Work through Java concepts and compare solutions together. Bring"
                                    + " one question to each session.",
                                "A friendly space to practise programming, one problem at a time.",
                                "Review database concepts and prepare for the next assessment."
                            }
                            [i]);
            group.setStudyGoal(StudyGoal.values()[i]);
            group.setPreferredMode(StudyMode.values()[i]);
            group.setAvailability(
                    List.of(
                            new AvailabilitySlot(
                                    DayOfWeek.WEDNESDAY,
                                    LocalTime.of(19, 0),
                                    LocalTime.of(21, 0))));
            group.setMaximumGroupSize(5);
            group.setLeaderId("S00" + (i + 1));
            group.setMemberIds(List.of(group.getLeaderId(), "S0" + (11 + i)));
            repository.save(group.getId(), group);
        }
        repository.save("v1", new SeedMarker(true));
    }
}
