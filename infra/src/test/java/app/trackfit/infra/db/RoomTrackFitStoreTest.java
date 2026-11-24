package app.trackfit.infra.db;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
import app.trackfit.domain.engine.EngineResult;
import app.trackfit.domain.engine.ProgramDraft;
import app.trackfit.domain.engine.TrainingEngine;
import app.trackfit.domain.model.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RoomTrackFitStoreTest {
    private static final Instant START = Instant.parse("2026-11-24T08:00:00Z");
    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    @Test
    public void catalogRoundTripsAllExerciseTypesAndPositions() {
        var context = RuntimeEnvironment.getApplication();
        try (TrackFitDatabase database = Room.inMemoryDatabaseBuilder(context, TrackFitDatabase.class)
                .allowMainThreadQueries().build()) {
            var store = new RoomTrackFitStore(database);
            var before = program();
            store.save(before);
            assertEquals(List.of(before), store.programs());
            var edited = new ProgramDraft(before).moveDay(1, 0).removeExercise(1, 2).rename("Edited").snapshot();
            store.save(edited);
            assertEquals(edited, store.load(before.id()).orElseThrow());
            assertFalse(store.load(new ProgramId(UUID.randomUUID())).isPresent());
        }
    }

    @Test
    public void sessionAndReportSurviveDatabaseCloseAndReopen() {
        Context context = RuntimeEnvironment.getApplication();
        String filename = "reopen-" + UUID.randomUUID() + ".db";
        Program program = program();
        TrainingState running;
        try (TrackFitDatabase database = open(context, filename)) {
            var store = new RoomTrackFitStore(database);
            store.save(program);
            var started = TrainingEngine.startProgram(TrainingState.empty(), program, 0, UUID.randomUUID(),
                    START, ZONE, store).state();
            var inProgress = TrainingEngine.startWorkout(started, START, ZONE, store).state();
            var work = (CycleDay.Work) program.days().get(0);
            running = TrainingEngine.checkExercise(inProgress, work.exercises().get(1).id(),
                    START.plusSeconds(1), ZONE, store).state();
            store.commit(running, List.of());
        }
        EngineResult result;
        try (TrackFitDatabase database = open(context, filename)) {
            var store = new RoomTrackFitStore(database);
            assertEquals(running, store.loadTraining());
            result = TrainingEngine.finishWorkout(running, START.plusSeconds(2), ZONE, store);
            store.commit(result.state(), result.logAppends());
        }
        try (TrackFitDatabase database = open(context, filename)) {
            var store = new RoomTrackFitStore(database);
            assertEquals(result.state(), store.loadTraining());
            assertEquals(result.logAppends(), store.entries());
            assertEquals(0, store.fullCompletionsOn(START.atZone(ZONE).toLocalDate()));
            var later = TrainingEngine.reconcile(store.loadTraining(), START.plusSeconds(3 * 86400L), ZONE, store);
            store.commit(later.state(), later.logAppends());
            assertEquals(later.state(), store.loadTraining());
            assertTrue(later.state().reports().get(1) instanceof Report.Cycle);
        } finally {
            context.deleteDatabase(filename);
        }
    }

    @Test
    public void failedLogWriteRollsBackRunAndReports() {
        var context = RuntimeEnvironment.getApplication();
        try (TrackFitDatabase database = Room.inMemoryDatabaseBuilder(context, TrackFitDatabase.class)
                .allowMainThreadQueries().build()) {
            var store = new RoomTrackFitStore(database);
            var program = program();
            var state = TrainingEngine.startProgram(TrainingState.empty(), program, 0, UUID.randomUUID(),
                    START, ZONE, store).state();
            var closed = TrainingEngine.reconcile(state, START.plusSeconds(86400L), ZONE, store);
            store.commit(closed.state(), closed.logAppends());
            assertThrows(RuntimeException.class, () -> store.commit(TrainingState.empty(), closed.logAppends()));
            assertEquals(closed.state(), store.loadTraining());
            assertEquals(closed.logAppends(), store.entries());
        }
    }

    private static TrackFitDatabase open(Context context, String filename) {
        return Room.databaseBuilder(context, TrackFitDatabase.class, filename).allowMainThreadQueries().build();
    }

    private static Program program() {
        var work = new CycleDay.Work(new DayId(UUID.randomUUID()), "Workout", 45, LocalTime.of(18, 30), List.of(
                new Exercise.Strength(exerciseId(), "Press", "Note", 3, 8, 25.5, 120),
                new Exercise.Cardio(exerciseId(), "Run", null, 180, "Fast", 110),
                new Exercise.Balance(exerciseId(), "Balance", null, 20, null, Exercise.BodySide.LEFT),
                new Exercise.Stretch(exerciseId(), "Stretch", null, 30, 2, Exercise.BodySide.BOTH)));
        return new Program(new ProgramId(UUID.randomUUID()), "Sample",
                List.of(work, new CycleDay.Rest(new DayId(UUID.randomUUID()))));
    }

    private static ExerciseId exerciseId() {
        return new ExerciseId(UUID.randomUUID());
    }
}
