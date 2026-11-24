package app.trackfit.domain.model;

import java.time.Instant;

public sealed interface WorkState permits WorkState.NotStarted, WorkState.Running, WorkState.Closed {
    enum Status { NOT_STARTED, RUNNING, COMPLETED, INCOMPLETE, SKIPPED }
    enum Result {
        COMPLETED(Status.COMPLETED), INCOMPLETE(Status.INCOMPLETE), SKIPPED(Status.SKIPPED);

        private final Status status;

        Result(Status status) {
            this.status = status;
        }
    }

    boolean autoStartEnabled();
    Status status();

    record NotStarted(boolean autoStartEnabled) implements WorkState {
        @Override public Status status() { return Status.NOT_STARTED; }
    }

    record Running(boolean autoStartEnabled, Session session) implements WorkState {
        public Running {
            java.util.Objects.requireNonNull(session);
        }

        @Override public Status status() { return Status.RUNNING; }
    }

    record Closed(boolean autoStartEnabled, Result result, Session session,
                  Instant closedAt, Session.CloseReason reason) implements WorkState {
        @Override public Status status() { return result.status; }
    }
}
