package app.trackfit.domain.engine;

import static app.trackfit.domain.engine.ExerciseSpecs.Field.*;
import static app.trackfit.domain.engine.ProgramValidator.Code.*;
import static org.junit.Assert.*;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class ProgramModelTest {
    private final ProgramId programId = new ProgramId(UUID.randomUUID());
    private final DayId day = new DayId(UUID.randomUUID());
    private final DayId rest = new DayId(UUID.randomUUID());
    private final ExerciseId lift = new ExerciseId(UUID.randomUUID());
    private final ExerciseId movement = new ExerciseId(UUID.randomUUID());
    private final Exercise.Strength pushUp = new Exercise.Strength(lift, "Push-up", null, 1, 1, null, null);
    private final Exercise.Cardio run = new Exercise.Cardio(movement, "Run", null, 60, null, null);

    @Test
    public void emptyAndRestOnlyProgramsCannotBeSaved() {
        var empty = ProgramDraft.empty(programId);
        assertEquals(List.of(PROGRAM_NAME, EMPTY_CYCLE, NO_WORK_DAY), codes(empty.snapshot()));
        assertEquals(List.of(NO_WORK_DAY), codes(empty.rename("Week").addRest(rest).snapshot()));
    }

    @Test
    public void dayOperationsPreserveOriginalAndNumberOnlyWorkDays() {
        var saved = new Program(programId, "Week", List.of(new CycleDay.Rest(rest)));
        DayId second = new DayId(UUID.randomUUID());
        var draft = new ProgramDraft(saved).addWork(day, n -> "Workout " + n)
                .addWork(second, n -> "Workout " + n).moveDay(2, 0).removeDay(2);
        assertEquals(List.of(new CycleDay.Rest(rest)), saved.days());
        assertEquals(new CycleDay.Work(second, "Workout 2", 0, null, List.of()), draft.snapshot().days().get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> draft.moveDay(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> draft.moveDay(0, 2));
        assertThrows(IllegalArgumentException.class, () -> draft.editWork(1, "Rest", 30, null));
        assertEquals(List.of(DAY_DURATION, EMPTY_WORK_DAY), codes(draft.snapshot()));
    }

    @Test
    public void workoutAndExerciseEditingKeepOrderAndStableIdentity() {
        var draft = ProgramDraft.empty(programId).rename("Week").addWork(day, n -> "Workout " + n);
        var populated = draft.editWork(0, "Strength", 1, LocalTime.NOON)
                .addExercise(0, pushUp).addExercise(0, run);
        var renamed = new Exercise.Strength(lift, "Press", "Slow", 2, 3, 0.0, 0);
        var edited = populated.editExercise(0, 0, renamed);
        var work = (CycleDay.Work) edited.snapshot().days().get(0);
        assertEquals(List.of(renamed, run), work.exercises());
        assertEquals(List.of(pushUp, run), ((CycleDay.Work) populated.snapshot().days().get(0)).exercises());
        assertEquals(LocalTime.NOON, work.autoStart());
        assertEquals(List.of(), codes(edited.snapshot()));
        assertEquals(List.of(run), ((CycleDay.Work) edited.removeExercise(0, 0).snapshot().days().get(0)).exercises());
        assertThrows(IllegalArgumentException.class, () -> edited.editExercise(0, 0, run));
        assertThrows(IllegalArgumentException.class, () -> edited.editExercise(0, 0,
                new Exercise.Strength(movement, "Press", null, 1, 1, null, null)));
    }

    @Test
    public void issuesLocateInvalidFieldsAndDuplicatedIdentities() {
        var bad = new Exercise.Strength(lift, " ", null, 0, -1, -1.0, -1);
        var program = new Program(programId, "Week", List.of(new CycleDay.Rest(day),
                new CycleDay.Work(day, "", 0, null, List.of(bad, pushUp))));
        var issues = ProgramValidator.validate(program);
        assertEquals(List.of(DUPLICATE_DAY, DAY_NAME, DAY_DURATION, EXERCISE_FIELD, EXERCISE_FIELD,
                EXERCISE_FIELD, EXERCISE_FIELD, EXERCISE_FIELD, DUPLICATE_EXERCISE), codes(program));
        assertEquals(new ProgramValidator.Issue(EXERCISE_FIELD, day, lift, NAME), issues.get(3));
        assertEquals(List.of(NAME, SETS, REPS, WEIGHT_KG, REST_SECONDS), ProgramValidator.invalidFields(bad));
        assertEquals(List.of(WEIGHT_KG), ProgramValidator.invalidFields(
                new Exercise.Strength(lift, "Press", null, 1, 1, Double.NaN, null)));
    }

    @Test
    public void fieldRulesAndSummariesCoverAllTypes() {
        var cardio = new Exercise.Cardio(movement, "Run", "Note", 0, "Fast", 0);
        var balance = new Exercise.Balance(movement, "Balance", null, -1, 0, Exercise.BodySide.LEFT);
        var stretch = new Exercise.Stretch(movement, "Stretch", null, 15, 1, Exercise.BodySide.BOTH);
        assertEquals(List.of(DURATION_SECONDS, TARGET_PULSE), ProgramValidator.invalidFields(cardio));
        assertEquals(List.of(DURATION_SECONDS, SETS), ProgramValidator.invalidFields(balance));
        assertEquals(List.of(), ProgramValidator.invalidFields(stretch));
        assertEquals(List.of(), ProgramValidator.invalidFields(
                new Exercise.Cardio(movement, "Run", null, 1, null, 1)));
        assertEquals(List.of(), ProgramValidator.invalidFields(
                new Exercise.Balance(movement, "Balance", null, 1, null, null)));
        assertEquals(List.of(), ProgramValidator.invalidFields(
                new Exercise.Stretch(movement, "Stretch", null, 1, null, null)));
        assertEquals(List.of(NAME, SETS, REPS, WEIGHT_KG), ExerciseSpecs.forType(pushUp.type()).summary());
        assertEquals(List.of(NAME, DURATION_SECONDS, INTENSITY), ExerciseSpecs.forType(cardio.type()).summary());
        assertEquals(List.of(NAME, DURATION_SECONDS, SETS), ExerciseSpecs.forType(balance.type()).summary());
        assertEquals(ExerciseSpecs.forType(balance.type()), ExerciseSpecs.forType(stretch.type()));
        assertNull(ExerciseSpecs.value(cardio, SETS));
        assertNull(ExerciseSpecs.value(pushUp, DURATION_SECONDS));
    }

    @Test
    public void collectionsAreDefensiveSnapshots() {
        var exercises = new ArrayList<Exercise>(List.of(pushUp));
        var work = new CycleDay.Work(day, "Work", 30, null, exercises);
        var days = new ArrayList<CycleDay>(List.of(work));
        var program = new Program(programId, "Week", days);
        exercises.clear();
        days.clear();
        assertEquals(List.of(work), program.days());
        assertEquals(List.of(pushUp), work.exercises());
        assertThrows(UnsupportedOperationException.class, () -> program.days().clear());
        assertThrows(UnsupportedOperationException.class, () -> work.exercises().clear());
    }

    private static List<ProgramValidator.Code> codes(Program program) {
        return ProgramValidator.validate(program).stream().map(ProgramValidator.Issue::code).toList();
    }
}
