package app.trackfit.domain.controller;

import app.trackfit.domain.engine.EngineResult;
import app.trackfit.domain.engine.ProgramValidator;
import java.util.List;

public sealed interface DomainError permits DomainError.Rejected, DomainError.ValidationFailed,
        DomainError.CommandFailure, DomainError.StorageFailure {
    record Rejected(EngineResult.Rejection reason) implements DomainError {}
    record ValidationFailed(List<ProgramValidator.Issue> issues) implements DomainError {}
    record StorageFailure(Throwable cause) implements DomainError {}

    enum CommandFailure implements DomainError {
        PROGRAM_NOT_FOUND, PROGRAM_NOT_EDITABLE
    }
}
