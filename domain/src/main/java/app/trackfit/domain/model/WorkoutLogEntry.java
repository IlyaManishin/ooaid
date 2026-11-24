package app.trackfit.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record WorkoutLogEntry(UUID id, LocalDate date, String programName, String dayName,
                              WorkState.Result result, Session.CloseReason reason, Instant startedAt,
                              Instant closedAt, int cycleNumber, List<ExerciseResult> exercises) {
    public WorkoutLogEntry {
        exercises = Collections.unmodifiableList(new ArrayList<>(exercises));
    }

    public record ExerciseResult(String name, Exercise.Type type, boolean checked) {}
}
