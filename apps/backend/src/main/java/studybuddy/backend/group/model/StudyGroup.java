package studybuddy.backend.group.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import studybuddy.backend.student.model.AvailabilitySlot;
import studybuddy.backend.student.model.StudyGoal;
import studybuddy.backend.student.model.StudyMode;

import java.util.ArrayList;
import java.util.List;

public class StudyGroup {
    private String id;
    @NotBlank private String courseCode;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String description;

    @NotNull private StudyGoal studyGoal;
    @NotNull private StudyMode preferredMode;

    @NotEmpty
    @Size(max = 21)
    @Valid
    private List<AvailabilitySlot> availability = new ArrayList<>();

    @Min(2)
    @Max(20)
    private int maximumGroupSize;

    private String leaderId;
    private List<String> memberIds = new ArrayList<>();
    private GroupStatus status = GroupStatus.ACTIVE;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StudyGoal getStudyGoal() {
        return studyGoal;
    }

    public void setStudyGoal(StudyGoal studyGoal) {
        this.studyGoal = studyGoal;
    }

    public StudyMode getPreferredMode() {
        return preferredMode;
    }

    public void setPreferredMode(StudyMode preferredMode) {
        this.preferredMode = preferredMode;
    }

    public List<AvailabilitySlot> getAvailability() {
        return availability;
    }

    public void setAvailability(List<AvailabilitySlot> availability) {
        this.availability =
                availability == null ? new ArrayList<>() : new ArrayList<>(availability);
    }

    public int getMaximumGroupSize() {
        return maximumGroupSize;
    }

    public void setMaximumGroupSize(int maximumGroupSize) {
        this.maximumGroupSize = maximumGroupSize;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds == null ? new ArrayList<>() : new ArrayList<>(memberIds);
    }

    public GroupStatus getStatus() {
        return status;
    }

    public void setStatus(GroupStatus status) {
        this.status = status;
    }
}
