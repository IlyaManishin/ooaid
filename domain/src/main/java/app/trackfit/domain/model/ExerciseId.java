package app.trackfit.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ExerciseId(UUID value) {
    public ExerciseId {
        Objects.requireNonNull(value);
    }
}
