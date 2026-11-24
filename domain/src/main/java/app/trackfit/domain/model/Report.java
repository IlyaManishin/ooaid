package app.trackfit.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public sealed interface Report permits Report.Day, Report.Cycle {
    UUID id();
    Instant createdAt();
    boolean viewed();
    Report markViewed();

    record Day(UUID id, Instant createdAt, boolean viewed, LocalDate date, String dayName,
               WorkState.Result result, int checked, int total, boolean earlierSuccess) implements Report {
        @Override
        public Day markViewed() {
            return new Day(id, createdAt, true, date, dayName, result, checked, total, earlierSuccess);
        }
    }

    record Cycle(UUID id, Instant createdAt, boolean viewed, int cycleNumber,
                 CycleOutcome outcome) implements Report {
        @Override
        public Cycle markViewed() {
            return new Cycle(id, createdAt, true, cycleNumber, outcome);
        }
    }
}
