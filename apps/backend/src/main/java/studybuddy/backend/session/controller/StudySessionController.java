package studybuddy.backend.session.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.auth.SessionService;
import studybuddy.backend.session.model.StudySession;
import studybuddy.backend.session.service.StudySessionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class StudySessionController {
    private final StudySessionService sessions;
    private final SessionService auth;

    public StudySessionController(StudySessionService sessions, SessionService auth) {
        this.sessions = sessions;
        this.auth = auth;
    }

    @PostMapping
    public StudySession create(@Valid @RequestBody StudySession s, HttpSession session) {
        return sessions.createSession(s, auth.actor(session));
    }

    @GetMapping
    public List<StudySession> forGroup(@RequestParam String groupId, HttpSession session) {
        return sessions.forGroup(groupId, auth.actor(session));
    }

    @GetMapping("/mine")
    public List<StudySession> mine(HttpSession session) {
        return sessions.bookable(auth.actor(session));
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Boolean> cancel(@PathVariable String id, HttpSession session) {
        sessions.cancelSession(id, auth.actor(session));
        return Map.of("success", true);
    }
}
