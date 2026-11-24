package app.trackfit.domain.ports;

import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkoutLogEntry;
import java.util.List;

public interface TrainingStateStore {
    TrainingState loadTraining();
    void commit(TrainingState state, List<WorkoutLogEntry> logAppends);
}
