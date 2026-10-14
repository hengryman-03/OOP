package studybuddy.backend.matching.model;

import studybuddy.backend.student.model.StudentProfile;

public class MatchResult {
    private StudentProfile student;
    private int score;
    private MatchBreakdown breakdown;
    private String explanation;

    public MatchResult() {}

    public MatchResult(StudentProfile student, MatchBreakdown breakdown) {
        this.student = student;
        this.breakdown = breakdown;
        this.score = breakdown.total();
        this.explanation = breakdown.explanation();
    }

    public StudentProfile getStudent() {
        return student;
    }

    public void setStudent(StudentProfile student) {
        this.student = student;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public MatchBreakdown getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(MatchBreakdown breakdown) {
        this.breakdown = breakdown;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
