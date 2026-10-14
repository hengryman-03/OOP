package studybuddy.backend.common;

import studybuddy.backend.student.model.AvailabilitySlot;

import java.util.List;

/** Shared timetable invariants keep profile and group input consistent. */
public final class StudyValidation {
    private StudyValidation() {}

    public static void slots(List<AvailabilitySlot> slots) {
        if (slots == null || slots.isEmpty() || slots.size() > 21)
            throw new IllegalArgumentException("Provide 1 to 21 availability slots.");
        for (AvailabilitySlot slot : slots) {
            if (slot == null
                    || slot.getDayOfWeek() == null
                    || slot.getStartTime() == null
                    || slot.getEndTime() == null
                    || !slot.getStartTime().isBefore(slot.getEndTime())
                    || slot.getStartTime().getSecond() != 0
                    || slot.getEndTime().getSecond() != 0
                    || slot.getStartTime().getNano() != 0
                    || slot.getEndTime().getNano() != 0) {
                throw new IllegalArgumentException(
                        "Each time slot needs a day and an end after its start, in whole minutes,"
                            + " within one day.");
            }
        }
    }
}
