package studybuddy.backend.venue.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import studybuddy.backend.student.model.AvailabilitySlot;

import java.util.ArrayList;
import java.util.List;

/** A fictional bookable study space; seeded once as demonstration data. */
public class Room {
    private String id;
    @NotBlank private String building;
    @NotBlank private String roomNumber;

    @Min(1)
    @Max(200)
    private int capacity;

    private boolean hasWhiteboard;
    private boolean hasProjector;
    private boolean hasPowerSockets;

    @NotEmpty
    @Size(max = 21)
    @Valid
    private List<AvailabilitySlot> weeklyAvailability = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isHasWhiteboard() {
        return hasWhiteboard;
    }

    public void setHasWhiteboard(boolean hasWhiteboard) {
        this.hasWhiteboard = hasWhiteboard;
    }

    public boolean isHasProjector() {
        return hasProjector;
    }

    public void setHasProjector(boolean hasProjector) {
        this.hasProjector = hasProjector;
    }

    public boolean isHasPowerSockets() {
        return hasPowerSockets;
    }

    public void setHasPowerSockets(boolean hasPowerSockets) {
        this.hasPowerSockets = hasPowerSockets;
    }

    public List<AvailabilitySlot> getWeeklyAvailability() {
        return weeklyAvailability;
    }

    public void setWeeklyAvailability(List<AvailabilitySlot> weeklyAvailability) {
        this.weeklyAvailability =
                weeklyAvailability == null ? new ArrayList<>() : new ArrayList<>(weeklyAvailability);
    }
}
