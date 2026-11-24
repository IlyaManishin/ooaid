package app.trackfit.domain.model;

import java.time.LocalDate;

public record DaySlot(LocalDate date, int dayIndex, WorkState work) {
    public DaySlot withWork(WorkState updated) {
        return new DaySlot(date, dayIndex, updated);
    }
}
