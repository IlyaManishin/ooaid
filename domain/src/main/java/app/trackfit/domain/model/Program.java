package app.trackfit.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record Program(ProgramId id, String name, List<CycleDay> days) {
    public Program {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        days = Collections.unmodifiableList(new ArrayList<>(days));
        days.forEach(Objects::requireNonNull);
    }
}
