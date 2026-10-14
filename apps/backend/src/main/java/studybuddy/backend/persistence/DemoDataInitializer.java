package studybuddy.backend.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.group.model.*;
import studybuddy.backend.student.model.*;
import studybuddy.backend.venue.model.Room;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
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

        // --- Demo venue data: fictional SMU-style rooms for the Venue Recommendation feature.
        // Clearly demonstration data — no real SMU room booking system is involved.
        record RoomSeed(
                String building,
                String roomNumber,
                int capacity,
                boolean whiteboard,
                boolean projector,
                boolean power,
                List<AvailabilitySlot> hours) {}
        List<RoomSeed> roomSeeds =
                List.of(
                        new RoomSeed("SCIS 1", "B1-1", 6, true, true, true, weekdays(9, 21)),
                        new RoomSeed("SCIS 1", "B1-2", 10, true, true, true, weekdays(9, 21)),
                        new RoomSeed("SCIS 1", "3-2", 4, true, false, true, weekdays(8, 20)),
                        new RoomSeed("SCIS 2", "GSR 2-1", 8, true, true, true, weekdays(9, 22)),
                        new RoomSeed("SCIS 2", "GSR 2-2", 12, false, true, true, weekdays(9, 22)),
                        new RoomSeed("SCIS 2", "GSR 2-3", 5, true, false, false, weekdays(9, 22)),
                        new RoomSeed(
                                "School of Economics", "SOE 1-3", 6, false, true, true, weekdays(8, 18)),
                        new RoomSeed(
                                "School of Economics", "SOE 2-1", 15, true, true, true, weekdays(8, 18)),
                        new RoomSeed(
                                "Li Ka Shing Library",
                                "LKS Discussion Rm 1",
                                4,
                                true,
                                false,
                                false,
                                sevenDays(8, 22)),
                        new RoomSeed(
                                "Li Ka Shing Library",
                                "LKS Discussion Rm 2",
                                8,
                                true,
                                true,
                                false,
                                sevenDays(8, 22)),
                        new RoomSeed(
                                "Li Ka Shing Library",
                                "LKS Discussion Rm 3",
                                4,
                                false,
                                false,
                                true,
                                sevenDays(8, 22)),
                        new RoomSeed(
                                "Administration Building",
                                "AB Seminar Rm 1",
                                20,
                                true,
                                true,
                                true,
                                weekdays(9, 17)),
                        new RoomSeed(
                                "Yong Pung How School of Law",
                                "YPHSL 2-4",
                                6,
                                true,
                                true,
                                true,
                                weekdays(9, 19)),
                        new RoomSeed(
                                "Yong Pung How School of Law",
                                "YPHSL 3-1",
                                10,
                                false,
                                true,
                                true,
                                weekdays(9, 19)),
                        new RoomSeed(
                                "Campus Green Building", "CGB Pod A", 3, false, false, true, sevenDays(10, 20)),
                        new RoomSeed(
                                "Campus Green Building", "CGB Pod B", 3, true, false, true, sevenDays(10, 20)),
                        new RoomSeed(
                                "Prinsep Street Residences",
                                "PSR Study Rm 1",
                                6,
                                true,
                                true,
                                true,
                                weekdays(10, 22)),
                        new RoomSeed(
                                "Prinsep Street Residences",
                                "PSR Study Rm 2",
                                12,
                                true,
                                true,
                                true,
                                weekdays(10, 22)));
        int roomIndex = 1;
        for (RoomSeed rs : roomSeeds) {
            Room room = new Room();
            room.setId("R" + String.format("%03d", roomIndex++));
            room.setBuilding(rs.building());
            room.setRoomNumber(rs.roomNumber());
            room.setCapacity(rs.capacity());
            room.setHasWhiteboard(rs.whiteboard());
            room.setHasProjector(rs.projector());
            room.setHasPowerSockets(rs.power());
            room.setWeeklyAvailability(rs.hours());
            repository.save(room.getId(), room);
        }

        repository.save("v1", new SeedMarker(true));
    }

    private static List<AvailabilitySlot> weekdays(int openHour, int closeHour) {
        return List.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY)
                .stream()
                .map(d -> new AvailabilitySlot(d, LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0)))
                .toList();
    }

    private static List<AvailabilitySlot> sevenDays(int openHour, int closeHour) {
        return Arrays.stream(DayOfWeek.values())
                .map(d -> new AvailabilitySlot(d, LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0)))
                .toList();
    }
}
