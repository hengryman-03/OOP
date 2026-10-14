package studybuddy.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "application", "Study Buddy Matcher System",
                "status", "running",
                "docs", "/api");
    }
}
