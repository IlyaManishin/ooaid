package app.trackfit.domain.model;

import java.util.Objects;
import java.util.UUID;

public record DayId(UUID value) {
    public DayId {
        Objects.requireNonNull(value);
    }
}
