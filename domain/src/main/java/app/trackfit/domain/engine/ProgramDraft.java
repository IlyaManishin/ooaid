package app.trackfit.domain.engine;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

public final class ProgramDraft {
    private final Program snapshot;

    public ProgramDraft(Program snapshot) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot);
    }

    public static ProgramDraft empty(ProgramId id) {
        return new ProgramDraft(new Program(id, "", Collections.emptyList()));
    }

    public Program snapshot() {
        return snapshot;
    }

    public ProgramDraft rename(String name) {
        return new ProgramDraft(new Program(snapshot.id(), name, snapshot.days()));
    }

    public ProgramDraft addWork(DayId id, IntFunction<String> nameForNumber) {
        int number = (int) snapshot.days().stream().filter(CycleDay.Work.class::isInstance).count() + 1;
        return append(new CycleDay.Work(id, nameForNumber.apply(number), 0, null, Collections.emptyList()));
    }

    public ProgramDraft addRest(DayId id) {
        return append(new CycleDay.Rest(id));
    }

    public ProgramDraft removeDay(int index) {
        var days = new ArrayList<>(snapshot.days());
        days.remove(index);
        return withDays(days);
    }

    public ProgramDraft moveDay(int from, int to) {
        var days = new ArrayList<>(snapshot.days());
        if (to < 0 || to >= days.size()) {
            throw new IndexOutOfBoundsException(Integer.toString(to));
        }
        days.add(to, days.remove(from));
        return withDays(days);
    }

    public ProgramDraft editWork(int index, String name, int durationMinutes, LocalTime autoStart) {
        return updateWork(index, work -> new CycleDay.Work(work.id(), name, durationMinutes, autoStart,
                work.exercises()));
    }

    public ProgramDraft addExercise(int dayIndex, Exercise exercise) {
        return updateExercises(dayIndex, exercises -> exercises.add(exercise));
    }

    public ProgramDraft removeExercise(int dayIndex, int exerciseIndex) {
        return updateExercises(dayIndex, exercises -> exercises.remove(exerciseIndex));
    }

    public ProgramDraft editExercise(int dayIndex, int exerciseIndex, Exercise replacement) {
        return updateExercises(dayIndex, exercises -> {
            Exercise original = exercises.get(exerciseIndex);
            if (original.type() != replacement.type()) {
                throw new IllegalArgumentException("Exercise type cannot change");
            }
            ExerciseId id = original.id();
            if (!id.equals(replacement.id())) {
                throw new IllegalArgumentException("Exercise ID cannot change");
            }
            exercises.set(exerciseIndex, replacement);
        });
    }

    private ProgramDraft append(CycleDay day) {
        var days = new ArrayList<>(snapshot.days());
        days.add(day);
        return withDays(days);
    }

    private ProgramDraft updateExercises(int dayIndex, java.util.function.Consumer<List<Exercise>> edit) {
        return updateWork(dayIndex, work -> {
            var exercises = new ArrayList<>(work.exercises());
            edit.accept(exercises);
            return new CycleDay.Work(work.id(), work.name(), work.durationMinutes(), work.autoStart(), exercises);
        });
    }

    private ProgramDraft updateWork(int index, UnaryOperator<CycleDay.Work> edit) {
        var days = new ArrayList<>(snapshot.days());
        if (!(days.get(index) instanceof CycleDay.Work work)) {
            throw new IllegalArgumentException("Rest days have no settings");
        }
        days.set(index, edit.apply(work));
        return withDays(days);
    }

    private ProgramDraft withDays(List<CycleDay> days) {
        return new ProgramDraft(new Program(snapshot.id(), snapshot.name(), days));
    }
}
