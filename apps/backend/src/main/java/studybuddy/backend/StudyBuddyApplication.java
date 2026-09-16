package studybuddy.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import studybuddy.backend.matching.config.MatchingProperties;

@SpringBootApplication
@EnableConfigurationProperties(MatchingProperties.class)
public class StudyBuddyApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudyBuddyApplication.class, args);
    }
}
