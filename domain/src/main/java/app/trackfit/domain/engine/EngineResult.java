package app.trackfit.domain.engine;

import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkoutLogEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EngineResult(TrainingState state, List<WorkoutLogEntry> logAppends,
                           List<DomainEvent> events, Rejection rejection) {
    public EngineResult {
        logAppends = Collections.unmodifiableList(new ArrayList<>(logAppends));
        events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public enum Rejection {
        ACTIVE_PROGRAM_EXISTS, INVALID_PROGRAM, INVALID_START_DAY, NO_ACTIVE_PROGRAM, CLOCK_ROLLBACK,
        NOT_TODAY, SESSION_NOT_RUNNING, WORKOUT_ALREADY_STARTED, UNKNOWN_EXERCISE, ALREADY_CHECKED,
        CYCLE_NOT_AWAITING_DECISION, REPORT_NOT_FOUND, CYCLE_REPORT_REQUIRES_DECISION
    }

    public boolean accepted() {
        return rejection == null;
    }
}
