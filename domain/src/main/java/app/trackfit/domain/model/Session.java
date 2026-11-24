package app.trackfit.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record Session(Instant startedAt, StartKind startKind, Instant deadlineAt,
                      CloseReason timeoutReason, Map<ExerciseId, Instant> checked) {
    public enum StartKind { MANUAL, AUTO }
    public enum CloseReason { MANUAL, DEADLINE, MIDNIGHT }

    public Session {
        checked = Collections.unmodifiableMap(new LinkedHashMap<>(checked));
    }

    public Session withCheck(ExerciseId exerciseId, Instant at) {
        Map<ExerciseId, Instant> updated = new LinkedHashMap<>(checked);
        updated.put(exerciseId, at);
        return new Session(startedAt, startKind, deadlineAt, timeoutReason, updated);
    }
}
