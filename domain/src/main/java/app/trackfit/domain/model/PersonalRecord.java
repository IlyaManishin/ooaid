package app.trackfit.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record PersonalRecord(UUID id, String exercise, LocalDate date, List<Parameter> parameters) {
    public PersonalRecord {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public record Parameter(String name, String value) {}
}
