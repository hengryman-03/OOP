package studybuddy.backend.matching.config;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.matching")
public class MatchingProperties {
    @NotNull
    @Pattern(regexp = "BALANCED|AVAILABILITY_FIRST|COURSE_FIRST")
    private String strategy = "BALANCED";

    @Min(0)
    @Max(1000)
    private int courseWeight = 40;

    @Min(0)
    @Max(1000)
    private int availabilityWeight = 25;

    @Min(0)
    @Max(1000)
    private int studyModeWeight = 15;

    @Min(0)
    @Max(1000)
    private int studyGoalWeight = 15;

    @Min(0)
    @Max(1000)
    private int groupSizeWeight = 5;

    @Min(1)
    @Max(50)
    private int maxResults = 10;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getCourseWeight() {
        return courseWeight;
    }

    public void setCourseWeight(int courseWeight) {
        this.courseWeight = courseWeight;
    }

    public int getAvailabilityWeight() {
        return availabilityWeight;
    }

    public void setAvailabilityWeight(int availabilityWeight) {
        this.availabilityWeight = availabilityWeight;
    }

    public int getStudyModeWeight() {
        return studyModeWeight;
    }

    public void setStudyModeWeight(int studyModeWeight) {
        this.studyModeWeight = studyModeWeight;
    }

    public int getStudyGoalWeight() {
        return studyGoalWeight;
    }

    public void setStudyGoalWeight(int studyGoalWeight) {
        this.studyGoalWeight = studyGoalWeight;
    }

    public int getGroupSizeWeight() {
        return groupSizeWeight;
    }

    public void setGroupSizeWeight(int groupSizeWeight) {
        this.groupSizeWeight = groupSizeWeight;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
}
