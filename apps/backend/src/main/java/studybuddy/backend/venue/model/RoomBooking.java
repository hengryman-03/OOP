package studybuddy.backend.venue.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A concrete date/time reservation of a {@link Room} for one {@code StudySession}. */
public class RoomBooking {
    private String id;
    private String roomId;
    private String sessionId;
    private String groupId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String bookedBy;
    private Instant bookedAt = Instant.now();

    public RoomBooking() {}

    public RoomBooking(
            String id,
            String roomId,
            String sessionId,
            String groupId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String bookedBy) {
        this.id = id;
        this.roomId = roomId;
        this.sessionId = sessionId;
        this.groupId = groupId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bookedBy = bookedBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public String getBookedBy() {
        return bookedBy;
    }

    public void setBookedBy(String bookedBy) {
        this.bookedBy = bookedBy;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public void setBookedAt(Instant bookedAt) {
        this.bookedAt = bookedAt;
    }
}
