package studybuddy.backend.admin.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import studybuddy.backend.admin.model.UserAccount;
import studybuddy.backend.admin.service.AdminService;
import studybuddy.backend.auth.SessionService;
import studybuddy.backend.matching.config.MatchingProperties;
import studybuddy.backend.matching.service.MatchingService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final MatchingService matching;
    private final SessionService sessions;

    public AdminController(AdminService admin, MatchingService matching, SessionService sessions) {
        this.admin = admin;
        this.matching = matching;
        this.sessions = sessions;
    }

    @GetMapping("/accounts")
    public List<AdminService.AccountUsage> accounts(HttpSession s) {
        return admin.listAccounts(sessions.actor(s));
    }

    @PostMapping("/accounts")
    public UserAccount save(@Valid @RequestBody UserAccount a, HttpSession s) {
        return admin.saveAccount(a, sessions.actor(s));
    }

    @DeleteMapping("/accounts/{id}")
    public Map<String, Boolean> delete(@PathVariable String id, HttpSession s) {
        admin.deleteAccount(id, sessions.actor(s));
        return Map.of("success", true);
    }

    @GetMapping("/matching-config")
    public MatchingProperties config(HttpSession s) {
        sessions.actor(s).requireAdmin();
        return matching.getCurrentConfig();
    }

    @PutMapping("/matching-config")
    public MatchingProperties configure(@Valid @RequestBody MatchingProperties c, HttpSession s) {
        return matching.updateConfig(c, sessions.actor(s));
    }
}
