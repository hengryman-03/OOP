package studybuddy.backend.connection.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.auth.SessionService;
import studybuddy.backend.connection.model.*;
import studybuddy.backend.connection.service.ConnectionService;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {
    private final ConnectionService connections;
    private final SessionService sessions;

    public ConnectionController(ConnectionService connections, SessionService sessions) {
        this.connections = connections;
        this.sessions = sessions;
    }

    @PostMapping("/requests")
    public BuddyRequest send(@Valid @RequestBody BuddyRequest request, HttpSession session) {
        return connections.sendRequest(request, sessions.actor(session));
    }

    @PostMapping("/requests/{requestId}/status")
    public BuddyRequest status(
            @PathVariable String requestId,
            @RequestParam RequestStatus status,
            HttpSession session) {
        return connections.updateStatus(requestId, status, sessions.actor(session));
    }

    @GetMapping("/students/{studentId}")
    public List<BuddyRequest> history(@PathVariable String studentId, HttpSession session) {
        sessions.actor(session).requireSelf(studentId);
        return connections.listForStudent(studentId);
    }

    @GetMapping("/students/{studentId}/active")
    public List<BuddyRequest> active(@PathVariable String studentId, HttpSession session) {
        sessions.actor(session).requireSelf(studentId);
        return connections.activeConnections(studentId);
    }
}
