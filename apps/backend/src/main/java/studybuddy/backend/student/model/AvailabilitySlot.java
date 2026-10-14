package studybuddy.backend.student.model;

import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class AvailabilitySlot {
    @NotNull private DayOfWeek dayOfWeek;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;

    public AvailabilitySlot() {}

    public AvailabilitySlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean overlapsWith(AvailabilitySlot other) {
        if (other == null || dayOfWeek != other.dayOfWeek) {
            return false;
        }
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
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
}
