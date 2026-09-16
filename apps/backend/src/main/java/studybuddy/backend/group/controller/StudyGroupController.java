package studybuddy.backend.group.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.auth.SessionService;
import studybuddy.backend.connection.model.RequestStatus;
import studybuddy.backend.group.model.*;
import studybuddy.backend.group.service.StudyGroupService;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {
    private final StudyGroupService groups;
    private final SessionService sessions;

    public StudyGroupController(StudyGroupService groups, SessionService sessions) {
        this.groups = groups;
        this.sessions = sessions;
    }

    @GetMapping
    public List<StudyGroup> list() {
        return groups.listGroups();
    }

    @GetMapping("/join-requests")
    public List<MembershipRequest> requests(HttpSession s) {
        return groups.requests(sessions.actor(s));
    }

    @PostMapping
    public StudyGroup create(@Valid @RequestBody StudyGroup g, HttpSession s) {
        return groups.createGroup(g, sessions.actor(s));
    }

    @PutMapping("/{id}")
    public StudyGroup update(
            @PathVariable String id, @Valid @RequestBody StudyGroup g, HttpSession s) {
        return groups.updateGroup(id, g, sessions.actor(s));
    }

    @PostMapping("/{id}/join-requests")
    public MembershipRequest join(@PathVariable String id, HttpSession s) {
        return groups.requestToJoin(id, sessions.actor(s));
    }

    @PostMapping("/join-requests/{id}/decision")
    public MembershipRequest decide(
            @PathVariable String id, @RequestParam RequestStatus decision, HttpSession s) {
        return groups.decideMembership(id, decision, sessions.actor(s));
    }

    @PostMapping("/{id}/remove-member")
    public StudyGroup remove(
            @PathVariable String id, @RequestParam String studentId, HttpSession s) {
        return groups.removeMember(id, studentId, sessions.actor(s));
    }

    @PostMapping("/{id}/transfer-leadership")
    public StudyGroup transfer(
            @PathVariable String id, @RequestParam String newLeaderId, HttpSession s) {
        return groups.transferLeadership(id, newLeaderId, sessions.actor(s));
    }

    @PostMapping("/{id}/close")
    public StudyGroup close(@PathVariable String id, HttpSession s) {
        return groups.closeGroup(id, sessions.actor(s));
    }

    @PostMapping("/{id}/leader-quits")
    public StudyGroup leave(
            @PathVariable String id,
            @RequestParam(required = false) String replacementLeaderId,
            @RequestParam(defaultValue = "false") boolean closeGroup,
            HttpSession s) {
        return groups.leaderQuits(id, replacementLeaderId, closeGroup, sessions.actor(s));
    }
}
