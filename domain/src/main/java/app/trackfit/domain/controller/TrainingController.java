package app.trackfit.domain.controller;

import app.trackfit.domain.engine.EngineResult;
import app.trackfit.domain.engine.TrainingEngine;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.domain.ports.TrainingStateStore;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.domain.ports.WorkoutLogRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class TrainingController {
    private final TrainingStateStore store;
    private final WorkoutLogRepository log;
    private final ProgramRepository programs;
    private final TransactionRunner transactions;
    private final TimeSource time;
    private final Executor writer;
    private final DomainEvents events;
    private final StatePublisher<TrainingState> state = new StatePublisher<>(TrainingState.empty());

    public TrainingController(TrainingStateStore store, WorkoutLogRepository log, ProgramRepository programs,
                              TransactionRunner transactions, TimeSource time, Executor writer, DomainEvents events) {
        this.store = store;
        this.log = log;
        this.programs = programs;
        this.transactions = transactions;
        this.time = time;
        this.writer = writer;
        this.events = events;
    }

    public Observation<TrainingState> state() {
        return state;
    }

    public CompletableFuture<Outcome<TrainingState>> reconcile() {
        return execute((previous, now, zone) -> TrainingEngine.reconcile(previous, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> startProgram(ProgramId id, int startDay) {
        return submit(() -> {
            record Start(EngineResult result, boolean missing) {}
            Start change = transactions.inTransaction(() -> {
                Optional<app.trackfit.domain.model.Program> program = programs.load(id);
                if (!program.isPresent()) {
                    return new Start(reconcileInsideTransaction(), true);
                }
                return new Start(apply((previous, now, zone) -> TrainingEngine.startProgram(previous, program.get(),
                        startDay, UUID.randomUUID(), now, zone, log)), false);
            });
            publish(change.result());
            return change.missing() ? Outcome.failure(DomainError.CommandFailure.PROGRAM_NOT_FOUND)
                    : outcome(change.result());
        });
    }

    public CompletableFuture<Outcome<TrainingState>> stopProgram() {
        return execute((previous, now, zone) -> TrainingEngine.stopProgram(previous, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> startWorkout() {
        return execute((previous, now, zone) -> TrainingEngine.startWorkout(previous, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> checkExercise(ExerciseId exercise) {
        return execute((previous, now, zone) -> TrainingEngine.checkExercise(previous, exercise, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> finishWorkout() {
        return execute((previous, now, zone) -> TrainingEngine.finishWorkout(previous, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> decideCycle(TrainingEngine.CycleDecision decision) {
        return execute((previous, now, zone) -> TrainingEngine.decideCycle(previous, decision, now, zone, log));
    }

    public CompletableFuture<Outcome<TrainingState>> markReportViewed(UUID id) {
        return execute((previous, now, zone) -> TrainingEngine.markReportViewed(previous, id, now, zone, log));
    }

    public Optional<Instant> nextTransitionAt(TrainingState snapshot) {
        return TrainingEngine.nextTransitionAt(snapshot, time.zone());
    }

    EngineResult reconcileInsideTransaction() {
        return apply((previous, now, zone) -> TrainingEngine.reconcile(previous, now, zone, log));
    }

    void publish(EngineResult result) {
        state.update(result.state());
        result.events().forEach(events::publish);
    }

    <T> CompletableFuture<Outcome<T>> submit(Supplier<Outcome<T>> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (RuntimeException error) {
                return Outcome.failure(new DomainError.StorageFailure(error));
            }
        }, writer);
    }

    private EngineResult apply(Command command) {
        TrainingState previous = store.loadTraining();
        EngineResult result = command.evaluate(previous, time.now(), time.zone());
        if (!previous.equals(result.state()) || !result.logAppends().isEmpty()) {
            store.commit(result.state(), result.logAppends());
        }
        return result;
    }

    private CompletableFuture<Outcome<TrainingState>> execute(Command command) {
        return submit(() -> {
            EngineResult result = transactions.inTransaction(() -> apply(command));
            publish(result);
            return outcome(result);
        });
    }

    private static Outcome<TrainingState> outcome(EngineResult result) {
        return result.accepted() ? Outcome.success(result.state())
                : Outcome.failure(new DomainError.Rejected(result.rejection()));
    }

    @FunctionalInterface
    private interface Command {
        EngineResult evaluate(TrainingState previous, Instant now, ZoneId zone);
    }
}
