package app.trackfit.domain.ports;

import app.trackfit.domain.engine.LogFacts;
import app.trackfit.domain.model.WorkoutLogEntry;
import java.util.List;

public interface WorkoutLogRepository extends LogFacts {
    List<WorkoutLogEntry> entries();
}
