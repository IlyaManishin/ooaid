package app.trackfit.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public sealed interface CyclePhase permits CyclePhase.InProgress, CyclePhase.AwaitingDecision {
    List<DaySlot> slots();

    record InProgress(List<DaySlot> slots) implements CyclePhase {
        public InProgress {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }

    record AwaitingDecision(List<DaySlot> slots, CycleOutcome outcome, UUID reportId) implements CyclePhase {
        public AwaitingDecision {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }
}
