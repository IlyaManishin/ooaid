package app.trackfit.app.presentation;

import static org.junit.Assert.*;

import android.os.Looper;
import app.trackfit.domain.controller.DomainEvents;
import app.trackfit.domain.controller.ProgramController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkoutLogEntry;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.domain.ports.TrainingStateStore;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.domain.ports.WorkoutLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class TrainingViewModelTest {
    private final MemoryStore store = new MemoryStore();
    private final FixedTime time = new FixedTime();
    private TrainingViewModel model;

    @Before
    public void createModel() {
        var training = new TrainingController(store, store, store, store, time, Runnable::run, new DomainEvents());
        var programs = new ProgramController(store, store, training);
        model = new TrainingViewModel(RuntimeEnvironment.getApplication(), programs, training, time);
        model.refresh();
        flush();
    }

    @After
    public void closeModel() { model.onCleared(); }

    @Test
    public void createStartAndCompleteWorkoutThroughViewModel() {
        assertEquals(TrainingViewModel.Mode.NO_PROGRAM, model.home().mode());
        model.newProgram();
        model.renameProgram("Program");
        int day = model.addWork();
        model.updateDay(day, "Day", "30", null);
        assertFalse(model.saveExercise("CARDIO", day, -1, Map.of("NAME", "Run", "DURATION_SECONDS", "1:60")));
        assertTrue(model.saveExercise("CARDIO", day, -1, Map.of(
                "NAME", "Run", "DURATION_SECONDS", "1:05", "NOTE", "Warm up")));
        model.saveDay();
        model.saveProgram(() -> {}, TrainingViewModelTest::fail);
        flush();
        var program = model.catalog().get(0);
        assertTrue(program.editEnabled());
        model.startProgram(program.id(), 0, () -> {}, TrainingViewModelTest::fail);
        flush();
        assertEquals(TrainingViewModel.Mode.READY, model.home().mode());
        assertFalse(model.catalog().get(0).editEnabled());
        model.startWorkout(() -> {}, TrainingViewModelTest::fail);
        flush();
        assertEquals(TrainingViewModel.Mode.RUNNING, model.home().mode());
        var exercise = model.home().day().exercises().get(0);
        assertEquals("Warm up", exercise.note());
        model.checkExercise(exercise.id(), TrainingViewModelTest::fail);
        flush();
        assertTrue(model.home().day().exercises().get(0).checked());
        model.finishWorkout(() -> {}, TrainingViewModelTest::fail);
        flush();
        assertTrue(model.nextReport().cycle());
        model.decideCycle(true, () -> {}, TrainingViewModelTest::fail);
        flush();
        assertEquals(TrainingViewModel.Mode.WAITING, model.home().mode());
        assertNull(model.nextReport());
    }

    @Test
    public void acceptsWholeMinutesAndPreservesExistingDurationFormat() {
        model.newProgram();
        int day = model.addWork();
        assertTrue(model.saveExercise("CARDIO", day, -1, Map.of("NAME", "Run", "DURATION_SECONDS", "5")));
        assertEquals("5:00", model.exerciseFields("CARDIO", day, 0).stream()
                .filter(field -> field.kind() == TrainingViewModel.InputKind.DURATION)
                .findFirst().orElseThrow().value());
        for (String invalid : List.of("5:", "5:60", "5:05:00", "0", "-5", "35791395")) {
            assertFalse(invalid, model.saveExercise("CARDIO", day, 0,
                    Map.of("NAME", "Run", "DURATION_SECONDS", invalid)));
        }
    }

    @Test
    public void backingOutOfNewDayRestoresPreviousDraft() {
        model.newProgram();
        model.addRest();
        int day = model.addWork();
        model.updateDay(day, "Draft", "20", null);
        model.discardDay();
        assertEquals(1, model.draft().days().size());
        assertFalse(model.draft().days().get(0).work());
        model.discardProgram();
        assertTrue(store.programs().isEmpty());
    }

    private static void flush() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private static void fail(String message) { org.junit.Assert.fail(message); }

    private static final class FixedTime implements TimeSource {
        @Override public Instant now() { return Instant.parse("2026-11-24T08:00:00Z"); }
        @Override public ZoneId zone() { return ZoneOffset.UTC; }
    }

    private static final class MemoryStore implements ProgramRepository, TrainingStateStore,
            WorkoutLogRepository, TransactionRunner {
        private final Map<ProgramId, Program> programs = new LinkedHashMap<>();
        private final List<WorkoutLogEntry> log = new ArrayList<>();
        private TrainingState state = TrainingState.empty();

        @Override public List<Program> programs() { return List.copyOf(programs.values()); }
        @Override public Optional<Program> load(ProgramId id) { return Optional.ofNullable(programs.get(id)); }
        @Override public void save(Program program) { programs.put(program.id(), program); }
        @Override public TrainingState loadTraining() { return state; }
        @Override public List<WorkoutLogEntry> entries() { return List.copyOf(log); }
        @Override public <T> T inTransaction(Supplier<T> transaction) { return transaction.get(); }

        @Override
        public void commit(TrainingState replacement, List<WorkoutLogEntry> entries) {
            state = replacement;
            log.addAll(entries);
        }

        @Override
        public int fullCompletionsOn(LocalDate date) {
            return (int) log.stream().filter(entry -> entry.date().equals(date)
                    && entry.result() == app.trackfit.domain.model.WorkState.Result.COMPLETED).count();
        }
    }
}
