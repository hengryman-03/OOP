package studybuddy.backend.student.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.auth.SessionService;
import studybuddy.backend.matching.model.MatchResult;
import studybuddy.backend.matching.service.MatchingService;
import studybuddy.backend.student.model.*;
import studybuddy.backend.student.service.StudentService;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService students;
    private final MatchingService matching;
    private final SessionService sessions;

    public StudentController(
            StudentService students, MatchingService matching, SessionService sessions) {
        this.students = students;
        this.matching = matching;
        this.sessions = sessions;
    }

    @GetMapping
    public List<StudentProfile> listStudents() {
        return students.findAll().stream().map(s -> s.toPublicProfile(false)).toList();
    }

    @GetMapping("/courses")
    public List<Course> courses() {
        return students.findCourses();
    }

    @GetMapping("/{studentId}")
    public StudentProfile profile(@PathVariable String studentId, HttpSession session) {
        return students.publicProfile(studentId, sessions.actor(session));
    }

    @PostMapping
    public StudentProfile save(@Valid @RequestBody StudentProfile profile, HttpSession session) {
        return students.save(profile, sessions.actor(session));
    }

    @GetMapping("/{studentId}/matches")
    public List<MatchResult> matches(
            @PathVariable String studentId,
            @RequestParam(required = false) String courseCode,
            @RequestParam(required = false) StudyGoal studyGoal,
            @RequestParam(required = false) StudyMode studyMode,
            @RequestParam(required = false) StudyArrangement arrangement,
            @RequestParam(required = false) DayOfWeek day,
            @RequestParam(required = false) LocalTime startTime,
            @RequestParam(required = false) LocalTime endTime,
            @RequestParam(defaultValue = "false") boolean overlapOnly,
            HttpSession session) {
        var actor = sessions.actor(session);
        actor.requireStudent();
        actor.requireSelf(studentId);
        return matching.findMatches(
                studentId,
                courseCode,
                studyGoal,
                studyMode,
                arrangement,
                day,
                startTime,
                endTime,
                overlapOnly);
    }
}
