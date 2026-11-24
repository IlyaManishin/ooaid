package app.trackfit.domain.coach;

import app.trackfit.domain.model.Report;

public interface CoachEvent {
    record AppEntered() implements CoachEvent {}
    record ReportPresented(Report report) implements CoachEvent {}
    record Tick() implements CoachEvent {}
}
