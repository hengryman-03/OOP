package studybuddy.backend.matching.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studybuddy.backend.auth.Actor;
import studybuddy.backend.common.StudyValidation;
import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.matching.model.*;
import studybuddy.backend.persistence.StudyRepository;
import studybuddy.backend.student.model.*;
import studybuddy.backend.student.service.StudentService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@Service
public class MatchingService {
    private final StudentService students;
    private final MatchingProperties defaults;
    private final StudyRepository repository;

    public MatchingService(
            StudentService students, MatchingProperties defaults, StudyRepository repository) {
        this.students = students;
        this.defaults = defaults;
        this.repository = repository;
    }

    public MatchingProperties getCurrentConfig() {
        return repository.find(MatchingProperties.class, "current").orElse(defaults);
    }

    @Transactional
    public MatchingProperties updateConfig(MatchingProperties config, Actor actor) {
        repository.lock();
        actor.requireAdmin();
        if (Arrays.stream(rawWeights(config)).sum() <= 0)
            throw new IllegalArgumentException(
                    "Enable at least one matching criterion with a positive weight.");
        return repository.save("current", config);
    }

    public List<MatchResult> findMatches(
            String studentId,
            String courseCode,
            StudyGoal goal,
            StudyMode mode,
            StudyArrangement arrangement,
            DayOfWeek day,
            LocalTime start,
            LocalTime end,
            boolean overlapOnly) {
        StudentProfile source = students.required(studentId);
        if (courseCode != null && !courseCode.isBlank()) students.requireCourse(courseCode);
        if ((start == null) != (end == null) || (start != null && day == null))
            throw new IllegalArgumentException(
                    "Time filtering requires a day, start time, and end time.");
        if (start != null) StudyValidation.slots(List.of(new AvailabilitySlot(day, start, end)));
        String targetCourse =
                courseCode == null || courseCode.isBlank()
                        ? source.getPreference().getCourseCode()
                        : courseCode;
        int[] weights = effectiveWeights(getCurrentConfig());
        return students.findAll().stream()
                .filter(s -> !s.getId().equals(studentId))
                .filter(
                        s ->
                                courseCode == null
                                        || courseCode.isBlank()
                                        || s.getCurrentCourses().contains(courseCode))
                .filter(s -> goal == null || s.getPreference().getStudyGoal() == goal)
                .filter(
                        s ->
                                mode == null
                                        || compatible(
                                                mode,
                                                s.getPreference().getPreferredMode(),
                                                StudyMode.EITHER))
                .filter(
                        s ->
                                arrangement == null
                                        || compatible(
                                                arrangement,
                                                s.getPreference().getPreferredArrangement(),
                                                StudyArrangement.EITHER))
                .filter(
                        s ->
                                !overlapOnly
                                        || AvailabilityCalculator.overlap(
                                                        source.getPreference().getAvailability(),
                                                        s.getPreference().getAvailability())
                                                > 0)
                .filter(
                        s ->
                                day == null
                                        || s.getPreference().getAvailability().stream()
                                                .anyMatch(
                                                        slot ->
                                                                slot.getDayOfWeek() == day
                                                                        && (start == null
                                                                                || slot
                                                                                        .overlapsWith(
                                                                                                new AvailabilitySlot(
                                                                                                        day,
                                                                                                        start,
                                                                                                        end)))))
                .map(
                        s ->
                                new MatchResult(
                                        s.toPublicProfile(false),
                                        score(source, s, targetCourse, weights)))
                .sorted(
                        Comparator.comparingInt(MatchResult::getScore)
                                .reversed()
                                .thenComparing(m -> m.getStudent().getId()))
                .limit(getCurrentConfig().getMaxResults())
                .toList();
    }

    private <T> boolean compatible(T a, T b, T either) {
        return a == either || b == either || a == b;
    }

    public MatchBreakdown score(
            StudentProfile source, StudentProfile candidate, String targetCourse, int[] weights) {
        StudyPreference a = source.getPreference(), b = candidate.getPreference();
        double course =
                targetCourse.equals(b.getCourseCode())
                        ? 1
                        : candidate.getCurrentCourses().contains(targetCourse) ? 0.5 : 0;
        int overlap = AvailabilityCalculator.overlap(a.getAvailability(), b.getAvailability());
        double availability =
                (double) overlap
                        / Math.max(
                                1,
                                AvailabilityCalculator.minutes(a.getAvailability()).cardinality());
        double size =
                compatible(
                                a.getPreferredArrangement(),
                                b.getPreferredArrangement(),
                                StudyArrangement.EITHER)
                        ? 1.0
                                / (1
                                        + Math.abs(
                                                a.getPreferredGroupSize()
                                                        - b.getPreferredGroupSize()))
                        : 0;
        return new MatchBreakdown(
                (int) Math.round(weights[0] * course),
                (int) Math.round(weights[1] * availability),
                compatible(a.getPreferredMode(), b.getPreferredMode(), StudyMode.EITHER)
                        ? weights[2]
                        : 0,
                a.getStudyGoal() == b.getStudyGoal() ? weights[3] : 0,
                (int) Math.round(weights[4] * size));
    }

    private int[] rawWeights(MatchingProperties c) {
        return new int[] {
            c.getCourseWeight(),
            c.getAvailabilityWeight(),
            c.getStudyModeWeight(),
            c.getStudyGoalWeight(),
            c.getGroupSizeWeight()
        };
    }

    /**
     * Strategies multiply one criterion by three; largest remainders keep the score ceiling exactly
     * 100.
     */
    public int[] effectiveWeights(MatchingProperties config) {
        int[] raw = rawWeights(config);
        if ("COURSE_FIRST".equals(config.getStrategy())) raw[0] *= 3;
        if ("AVAILABILITY_FIRST".equals(config.getStrategy())) raw[1] *= 3;
        double sum = Arrays.stream(raw).sum();
        if (sum <= 0)
            throw new IllegalArgumentException("At least one matching weight must be positive.");
        int[] weights = new int[5];
        double[] remainders = new double[5];
        for (int i = 0; i < 5; i++) {
            double scaled = raw[i] * 100 / sum;
            weights[i] = (int) scaled;
            remainders[i] = scaled - weights[i];
        }
        int remaining = 100 - Arrays.stream(weights).sum();
        for (int n = 0; n < remaining; n++) {
            int best = 0;
            for (int i = 1; i < 5; i++) if (remainders[i] > remainders[best]) best = i;
            weights[best]++;
            remainders[best] = -1;
        }
        return weights;
    }
}
