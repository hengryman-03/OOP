package studybuddy.backend.matching.model;

public class MatchBreakdown {
    private int courseScore;
    private int availabilityScore;
    private int studyModeScore;
    private int studyGoalScore;
    private int groupSizeScore;

    public MatchBreakdown() {}

    public MatchBreakdown(
            int courseScore,
            int availabilityScore,
            int studyModeScore,
            int studyGoalScore,
            int groupSizeScore) {
        this.courseScore = courseScore;
        this.availabilityScore = availabilityScore;
        this.studyModeScore = studyModeScore;
        this.studyGoalScore = studyGoalScore;
        this.groupSizeScore = groupSizeScore;
    }

    public int total() {
        return courseScore + availabilityScore + studyModeScore + studyGoalScore + groupSizeScore;
    }

    public String explanation() {
        return "course="
                + courseScore
                + ", availability="
                + availabilityScore
                + ", mode="
                + studyModeScore
                + ", goal="
                + studyGoalScore
                + ", groupSize="
                + groupSizeScore;
    }

    public int getCourseScore() {
        return courseScore;
    }

    public void setCourseScore(int courseScore) {
        this.courseScore = courseScore;
    }

    public int getAvailabilityScore() {
        return availabilityScore;
    }

    public void setAvailabilityScore(int availabilityScore) {
        this.availabilityScore = availabilityScore;
    }

    public int getStudyModeScore() {
        return studyModeScore;
    }

    public void setStudyModeScore(int studyModeScore) {
        this.studyModeScore = studyModeScore;
    }

    public int getStudyGoalScore() {
        return studyGoalScore;
    }

    public void setStudyGoalScore(int studyGoalScore) {
        this.studyGoalScore = studyGoalScore;
    }

    public int getGroupSizeScore() {
        return groupSizeScore;
    }

    public void setGroupSizeScore(int groupSizeScore) {
        this.groupSizeScore = groupSizeScore;
    }
}
