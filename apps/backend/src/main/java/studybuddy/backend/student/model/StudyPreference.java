package studybuddy.backend.student.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

public class StudyPreference {
    @NotBlank private String courseCode;
    @NotNull private StudyMode preferredMode;
    @NotNull private StudyArrangement preferredArrangement;
    @NotNull private StudyGoal studyGoal;

    @Min(2)
    @Max(20)
    private int preferredGroupSize;

    @NotEmpty
    @Size(max = 21)
    @Valid
    private List<AvailabilitySlot> availability = new ArrayList<>();

    public StudyPreference() {}

    public StudyPreference(
            String courseCode,
            StudyMode preferredMode,
            StudyArrangement preferredArrangement,
            StudyGoal studyGoal,
            int preferredGroupSize,
            List<AvailabilitySlot> availability) {
        this.courseCode = courseCode;
        this.preferredMode = preferredMode;
        this.preferredArrangement = preferredArrangement;
        this.studyGoal = studyGoal;
        this.preferredGroupSize = preferredGroupSize;
        this.availability =
                availability == null ? new ArrayList<>() : new ArrayList<>(availability);
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public StudyMode getPreferredMode() {
        return preferredMode;
    }

    public void setPreferredMode(StudyMode preferredMode) {
        this.preferredMode = preferredMode;
    }

    public StudyArrangement getPreferredArrangement() {
        return preferredArrangement;
    }

    public void setPreferredArrangement(StudyArrangement preferredArrangement) {
        this.preferredArrangement = preferredArrangement;
    }

    public StudyGoal getStudyGoal() {
        return studyGoal;
    }

    public void setStudyGoal(StudyGoal studyGoal) {
        this.studyGoal = studyGoal;
    }

    public int getPreferredGroupSize() {
        return preferredGroupSize;
    }

    public void setPreferredGroupSize(int preferredGroupSize) {
        this.preferredGroupSize = preferredGroupSize;
    }

    public List<AvailabilitySlot> getAvailability() {
        return availability;
    }

    public void setAvailability(List<AvailabilitySlot> availability) {
        this.availability =
                availability == null ? new ArrayList<>() : new ArrayList<>(availability);
    }
}
