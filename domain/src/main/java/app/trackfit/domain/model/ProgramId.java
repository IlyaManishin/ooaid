package app.trackfit.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ProgramId(UUID value) {
    public ProgramId {
        Objects.requireNonNull(value);
    }
}
