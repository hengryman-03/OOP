package studybuddy.backend.matching.service;

import studybuddy.backend.student.model.AvailabilitySlot;

import java.util.BitSet;
import java.util.List;

/** Minute sets measure real overlap without double-counting duplicate or overlapping slots. */
public final class AvailabilityCalculator {
    private AvailabilityCalculator() {}

    public static BitSet minutes(List<AvailabilitySlot> slots) {
        BitSet minutes = new BitSet(7 * 24 * 60);
        for (AvailabilitySlot slot : slots) {
            int day = (slot.getDayOfWeek().getValue() - 1) * 1440;
            minutes.set(
                    day + slot.getStartTime().toSecondOfDay() / 60,
                    day + slot.getEndTime().toSecondOfDay() / 60);
        }
        return minutes;
    }

    public static int overlap(List<AvailabilitySlot> a, List<AvailabilitySlot> b) {
        BitSet overlap = minutes(a);
        overlap.and(minutes(b));
        return overlap.cardinality();
    }
}
