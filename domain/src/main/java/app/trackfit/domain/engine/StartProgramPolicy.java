package app.trackfit.domain.engine;

import app.trackfit.domain.model.TrainingState;

public final class StartProgramPolicy {
    private StartProgramPolicy() {}

    public static boolean permits(TrainingState state) {
        return state.run() == null;
    }
}
