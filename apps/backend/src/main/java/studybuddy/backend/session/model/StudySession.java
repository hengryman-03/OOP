package studybuddy.backend.session.model;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

public class StudySession {
    private String id;
    @NotBlank private String groupId;

    @Size(max = 100)
    private String title;

    @NotNull private LocalDate sessionDate;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;

    @Min(2)
    @Max(20)
    private int maxGroupSize;

    private String plannedBy;
    private String bookedRoomId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getMaxGroupSize() {
        return maxGroupSize;
    }

    public void setMaxGroupSize(int maxGroupSize) {
        this.maxGroupSize = maxGroupSize;
    }

    public String getPlannedBy() {
        return plannedBy;
    }

    public void setPlannedBy(String plannedBy) {
        this.plannedBy = plannedBy;
    }

    public String getBookedRoomId() {
        return bookedRoomId;
    }

    public void setBookedRoomId(String bookedRoomId) {
        this.bookedRoomId = bookedRoomId;
    }
}
