package app.trackfit.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ProgramRun(UUID id, Program program, int startDayIndex, int cycleNumber,
                         CyclePhase phase, Instant lastReconciledAt) {
    public ProgramRun withPhase(CyclePhase updatedPhase, Instant reconciledAt) {
        return new ProgramRun(id, program, startDayIndex, cycleNumber, updatedPhase, reconciledAt);
    }
}
