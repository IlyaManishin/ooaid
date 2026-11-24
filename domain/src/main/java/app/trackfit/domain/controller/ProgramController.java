package app.trackfit.domain.controller;

import app.trackfit.domain.engine.EngineResult;
import app.trackfit.domain.engine.ProgramDraft;
import app.trackfit.domain.engine.ProgramValidator;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProgramController {
    public record ProgramSummary(ProgramId id, String name) {}

    private final ProgramRepository repository;
    private final TransactionRunner transactions;
    private final TrainingController training;
    private final StatePublisher<List<ProgramSummary>> programs = new StatePublisher<>(Collections.emptyList());

    public ProgramController(ProgramRepository repository, TransactionRunner transactions, TrainingController training) {
        this.repository = repository;
        this.transactions = transactions;
        this.training = training;
    }

    public Observation<List<ProgramSummary>> programs() {
        return programs;
    }

    public ProgramDraft createDraft() {
        return ProgramDraft.empty(new ProgramId(UUID.randomUUID()));
    }

    public CompletableFuture<Outcome<Program>> load(ProgramId id) {
        return training.submit(() -> repository.load(id).map(Outcome::success)
                .orElseGet(() -> Outcome.failure(DomainError.CommandFailure.PROGRAM_NOT_FOUND)));
    }

    public CompletableFuture<Outcome<List<ProgramSummary>>> refresh() {
        return training.submit(() -> Outcome.success(publishPrograms()));
    }

    public CompletableFuture<Outcome<Program>> save(ProgramDraft draft) {
        return training.submit(() -> {
            record Save(EngineResult training, Outcome<Program> outcome) {}
            Save result = transactions.inTransaction(() -> {
                EngineResult reconciled = training.reconcileInsideTransaction();
                var run = reconciled.state().run();
                Program program = draft.snapshot();
                if (run != null && run.program().id().equals(program.id())) {
                    return new Save(reconciled, Outcome.failure(DomainError.CommandFailure.PROGRAM_NOT_EDITABLE));
                }
                var issues = ProgramValidator.validate(program);
                if (!issues.isEmpty()) {
                    return new Save(reconciled, Outcome.failure(new DomainError.ValidationFailed(issues)));
                }
                repository.save(program);
                return new Save(reconciled, Outcome.success(program));
            });
            training.publish(result.training());
            if (result.outcome().succeeded()) publishPrograms();
            return result.outcome();
        });
    }

    private List<ProgramSummary> publishPrograms() {
        List<ProgramSummary> snapshot = new java.util.ArrayList<>();
        for (Program program : repository.programs()) {
            snapshot.add(new ProgramSummary(program.id(), program.name()));
        }
        List<ProgramSummary> immutable = Collections.unmodifiableList(snapshot);
        programs.update(immutable);
        return immutable;
    }
}
