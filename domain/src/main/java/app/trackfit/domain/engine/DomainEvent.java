package app.trackfit.domain.engine;

import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.WorkoutLogEntry;

public sealed interface DomainEvent permits DomainEvent.WorkoutClosed, DomainEvent.CycleCompleted,
        DomainEvent.ProgramSelectionRequested {
    record WorkoutClosed(WorkoutLogEntry entry) implements DomainEvent {}
    record CycleCompleted(Report.Cycle report) implements DomainEvent {}
    record ProgramSelectionRequested() implements DomainEvent {}
}
