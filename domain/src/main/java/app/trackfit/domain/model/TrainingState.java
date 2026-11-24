package app.trackfit.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TrainingState(ProgramRun run, List<Report> reports) {
    public TrainingState {
        reports = Collections.unmodifiableList(new ArrayList<>(reports));
    }

    public static TrainingState empty() {
        return new TrainingState(null, Collections.emptyList());
    }
}
