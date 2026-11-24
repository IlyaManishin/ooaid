package app.trackfit.domain.engine;

import static app.trackfit.domain.engine.EngineResult.Rejection.*;
import static app.trackfit.domain.model.WorkState.Result.*;
import static org.junit.Assert.*;

import app.trackfit.domain.model.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class TrainingEngineTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final LogFacts NO_LOG = date -> 0;
    private static final Instant MORNING = time("2026-11-24T08:00:00Z");
    private static final Instant NOON = time("2026-11-24T12:00:00Z");
    private static final Instant MIDNIGHT = time("2026-11-25T00:00:00Z");
    private final UUID runId = UUID.randomUUID();
    private final ExerciseId first = new ExerciseId(UUID.randomUUID());
    private final ExerciseId second = new ExerciseId(UUID.randomUUID());

    @Test
    public void chosenStartWrapsAndCanBeRest() {
        var work = work(null, 30);
        var rest = rest();
        var program = program(work, rest);
        TrainingState state = start(program, 1, MORNING, UTC);
        assertEquals(List.of(1, 0), slots(state).stream().map(DaySlot::dayIndex).toList());
        assertEquals(List.of(LocalDate.parse("2026-11-24"), LocalDate.parse("2026-11-25")),
                slots(state).stream().map(DaySlot::date).toList());
        assertNull(slots(state).get(0).work());
        assertEquals(NOT_TODAY, TrainingEngine.startWorkout(state, MORNING, UTC, NO_LOG).rejection());
        assertEquals(ACTIVE_PROGRAM_EXISTS,
                TrainingEngine.startProgram(state, program, 0, runId, MORNING, UTC, NO_LOG).rejection());
        assertEquals(INVALID_START_DAY, TrainingEngine.startProgram(TrainingState.empty(), program, -1,
                runId, MORNING, UTC, NO_LOG).rejection());
    }

    @Test
    public void manualFinishClassifiesEveryPossibleExerciseCount() {
        for (int count = 0; count <= 2; count++) {
            var state = startedWorkout(start(program(work(null, 60), rest()), 0, MORNING, UTC), MORNING);
            if (count >= 1) state = checked(state, first, MORNING.plusSeconds(1));
            if (count >= 2) state = checked(state, second, MORNING.plusSeconds(2));
            var output = TrainingEngine.finishWorkout(state, MORNING.plusSeconds(60), UTC, NO_LOG);
            assertTrue(output.accepted());
            var expected = List.of(SKIPPED, INCOMPLETE, COMPLETED).get(count);
            assertEquals(expected, output.logAppends().get(0).result());
            var report = (Report.Day) output.state().reports().get(0);
            assertEquals(expected, report.result());
            assertEquals(count, report.checked());
            assertEquals(count, output.logAppends().get(0).exercises().stream()
                    .filter(WorkoutLogEntry.ExerciseResult::checked).count());
        }
    }

    @Test
    public void repeatedUnknownAndLateChecksCannotChangeTheResult() {
        var state = startedWorkout(start(program(work(null, 30), rest()), 0, MORNING, UTC), MORNING);
        assertEquals(UNKNOWN_EXERCISE, TrainingEngine.checkExercise(state, new ExerciseId(UUID.randomUUID()),
                MORNING, UTC, NO_LOG).rejection());
        state = checked(state, first, MORNING);
        assertEquals(ALREADY_CHECKED, TrainingEngine.checkExercise(state, first, MORNING, UTC, NO_LOG).rejection());
        var deadline = MORNING.plusSeconds(1800);
        var late = TrainingEngine.checkExercise(state, second, deadline, UTC, NO_LOG);
        assertEquals(SESSION_NOT_RUNNING, late.rejection());
        assertEquals(INCOMPLETE, late.logAppends().get(0).result());
        assertEquals(deadline, late.logAppends().get(0).closedAt());
        assertEquals(Session.CloseReason.DEADLINE, late.logAppends().get(0).reason());
    }

    @Test
    public void autoStartUsesScheduledTimeEvenWhenApplicationWasClosed() {
        for (int elapsedMinutes : List.of(10, 30)) {
            var scheduled = time("2026-11-24T18:30:00Z");
            var state = start(program(work(LocalTime.of(18, 30), 30), rest()), 0, MORNING, UTC);
            var output = TrainingEngine.reconcile(state, scheduled.plusSeconds(elapsedMinutes * 60L), UTC, NO_LOG);
            if (elapsedMinutes == 10) {
                var running = (WorkState.Running) slots(output.state()).get(0).work();
                assertEquals(scheduled, running.session().startedAt());
                assertEquals(scheduled.plusSeconds(1800), running.session().deadlineAt());
                assertEquals(Session.StartKind.AUTO, running.session().startKind());
            } else {
                assertEquals(SKIPPED, output.logAppends().get(0).result());
                assertEquals(scheduled, output.logAppends().get(0).startedAt());
            }
        }
    }

    @Test
    public void manualStartMayPreemptAutoStartAndDeadlineNeverPassesMidnight() {
        var start = time("2026-11-24T23:30:00Z");
        var state = startedWorkout(start(program(work(LocalTime.of(23, 50), 90), rest()), 0, MORNING, UTC), start);
        var output = TrainingEngine.reconcile(state, MIDNIGHT, UTC, NO_LOG);
        assertEquals(Session.CloseReason.MIDNIGHT, output.logAppends().get(0).reason());
        assertEquals(MIDNIGHT, output.logAppends().get(0).closedAt());
        assertEquals(start, output.logAppends().get(0).startedAt());
    }

    @Test
    public void delayedReconcileProducesOrderedReportsAndOnlyOneCycleReport() {
        var state = start(program(work(null, 30), rest(), work(LocalTime.NOON, 30)), 0, MORNING, UTC);
        var now = time("2026-11-29T00:00:00Z");
        var output = TrainingEngine.reconcile(state, now, UTC, NO_LOG);
        assertEquals(2, output.logAppends().size());
        assertEquals(MIDNIGHT, output.logAppends().get(0).closedAt());
        assertEquals(time("2026-11-26T12:30:00Z"), output.logAppends().get(1).closedAt());
        assertTrue(output.state().reports().get(0) instanceof Report.Day);
        assertEquals(CycleOutcome.FULL_FAILURE, ((Report.Cycle) output.state().reports().get(1)).outcome());
        assertEquals(2, output.state().reports().size());
        var repeat = TrainingEngine.reconcile(output.state(), now, UTC, NO_LOG);
        assertEquals(output.state(), repeat.state());
        assertTrue(repeat.logAppends().isEmpty());
        assertTrue(repeat.events().isEmpty());
    }

    @Test
    public void lastWorkSuppressesDayReportAndCycleOutcomeUsesFinalStatuses() {
        for (int count : List.of(0, 1, 2)) {
            var state = startedWorkout(start(program(work(null, 30)), 0, MORNING, UTC), MORNING);
            if (count >= 1) state = checked(state, first, MORNING);
            if (count >= 2) state = checked(state, second, MORNING);
            var output = TrainingEngine.finishWorkout(state, MORNING, UTC, NO_LOG);
            assertEquals(1, output.state().reports().size());
            var report = (Report.Cycle) output.state().reports().get(0);
            assertEquals(List.of(CycleOutcome.FULL_FAILURE, CycleOutcome.PARTIAL_SUCCESS,
                    CycleOutcome.FULL_SUCCESS).get(count), report.outcome());
            assertEquals(CYCLE_REPORT_REQUIRES_DECISION,
                    TrainingEngine.markReportViewed(output.state(), report.id(), MORNING, UTC, NO_LOG).rejection());
        }
    }

    @Test
    public void restEndsCycleAtMidnightAndDecisionStartsTomorrowFromSameIndex() {
        var work = work(null, 30);
        var rest = rest();
        var state = start(program(rest, work), 1, MORNING, UTC);
        var restEnd = MIDNIGHT.plusSeconds(86400);
        assertTrue(TrainingEngine.reconcile(state, restEnd.minusSeconds(1), UTC, NO_LOG)
                .state().run().phase() instanceof CyclePhase.InProgress);
        var done = TrainingEngine.reconcile(state, restEnd, UTC, NO_LOG).state();
        assertEquals(restEnd, done.reports().get(1).createdAt());
        var switchProgram = TrainingEngine.decideCycle(done, TrainingEngine.CycleDecision.SWITCH_PROGRAM,
                restEnd, UTC, NO_LOG);
        assertNull(switchProgram.state().run());
        assertTrue(switchProgram.events().get(0) instanceof DomainEvent.ProgramSelectionRequested);
        assertTrue(switchProgram.state().reports().get(1).viewed());
        var continued = TrainingEngine.decideCycle(done, TrainingEngine.CycleDecision.CONTINUE,
                restEnd, UTC, NO_LOG).state();
        assertEquals(2, continued.run().cycleNumber());
        assertEquals(1, continued.run().startDayIndex());
        assertEquals(LocalDate.parse("2026-11-27"), slots(continued).get(0).date());
        assertEquals(WorkState.Status.NOT_STARTED, slots(continued).get(0).work().status());
    }

    @Test
    public void missedFirstAutoStartStaysManualAndScheduleTracksTransitions() {
        var program = program(work(LocalTime.NOON, 30), rest());
        var late = start(program, 0, NOON, UTC);
        assertFalse(slots(late).get(0).work().autoStartEnabled());
        assertEquals(MIDNIGHT, TrainingEngine.nextTransitionAt(late, UTC).orElseThrow());
        var early = start(program, 0, MORNING, UTC);
        assertEquals(NOON, TrainingEngine.nextTransitionAt(early, UTC).orElseThrow());
        var running = TrainingEngine.reconcile(early, NOON, UTC, NO_LOG).state();
        assertEquals(NOON.plusSeconds(1800), TrainingEngine.nextTransitionAt(running, UTC).orElseThrow());
        var restDay = TrainingEngine.reconcile(running, MIDNIGHT, UTC, NO_LOG).state();
        assertEquals(MIDNIGHT.plusSeconds(86400), TrainingEngine.nextTransitionAt(restDay, UTC).orElseThrow());
        assertFalse(TrainingEngine.nextTransitionAt(TrainingState.empty(), UTC).isPresent());
    }

    @Test
    public void clockRollbackDoesNotReopenClosedSlotAndZoneRecomputesPendingAutoStart() {
        var state = start(program(work(LocalTime.NOON, 30), rest()), 0, MORNING, UTC);
        var shifted = ZoneOffset.ofHours(2);
        var autoStart = time("2026-11-24T10:00:00Z");
        var result = TrainingEngine.reconcile(state, autoStart, shifted, NO_LOG);
        var running = (WorkState.Running) slots(result.state()).get(0).work();
        assertEquals(autoStart, running.session().startedAt());
        var closed = TrainingEngine.reconcile(result.state(), autoStart.plusSeconds(1800), shifted, NO_LOG).state();
        assertEquals(closed, TrainingEngine.reconcile(closed, MORNING, UTC, NO_LOG).state());
        assertEquals(CLOCK_ROLLBACK, TrainingEngine.startWorkout(closed, MORNING, UTC, NO_LOG).rejection());
    }

    @Test
    public void daylightSavingGapUsesResolvedTimeWithoutShiftingSlotDate() {
        var zone = ZoneId.of("Europe/Berlin");
        var start = time("2026-03-29T00:00:00Z");
        var state = start(program(work(LocalTime.of(2, 30), 30), rest()), 0, start, zone);
        var resolved = time("2026-03-29T01:30:00Z");
        assertEquals(resolved, TrainingEngine.nextTransitionAt(state, zone).orElseThrow());
        var output = TrainingEngine.reconcile(state, resolved, zone, NO_LOG).state();
        assertEquals(LocalDate.parse("2026-03-29"), slots(output).get(0).date());
        assertEquals(resolved, ((WorkState.Running) slots(output).get(0).work()).session().startedAt());
    }

    @Test
    public void priorSameDateSuccessIsCapturedBeforeWritingNewLogAndSurvivesStop() {
        var state = startedWorkout(start(program(work(null, 30), rest()), 0, MORNING, UTC), MORNING);
        state = checked(checked(state, first, MORNING), second, MORNING);
        var output = TrainingEngine.finishWorkout(state, MORNING, UTC,
                date -> date.equals(LocalDate.parse("2026-11-24")) ? 1 : 0);
        var report = (Report.Day) output.state().reports().get(0);
        assertTrue(report.earlierSuccess());
        var stopped = TrainingEngine.stopProgram(output.state(), MORNING, UTC, NO_LOG);
        assertNull(stopped.state().run());
        assertTrue(stopped.logAppends().isEmpty());
        var viewed = TrainingEngine.markReportViewed(stopped.state(), report.id(), MORNING, UTC, NO_LOG);
        assertTrue(viewed.state().reports().get(0).viewed());
    }

    private TrainingState start(Program program, int index, Instant at, ZoneId zone) {
        var result = TrainingEngine.startProgram(TrainingState.empty(), program, index, runId, at, zone, NO_LOG);
        assertTrue(result.accepted());
        return result.state();
    }

    private static TrainingState startedWorkout(TrainingState state, Instant at) {
        var result = TrainingEngine.startWorkout(state, at, UTC, NO_LOG);
        assertTrue(result.accepted());
        return result.state();
    }

    private static TrainingState checked(TrainingState state, ExerciseId id, Instant at) {
        var result = TrainingEngine.checkExercise(state, id, at, UTC, NO_LOG);
        assertTrue(result.accepted());
        return result.state();
    }

    private CycleDay.Work work(LocalTime autoStart, int duration) {
        return new CycleDay.Work(new DayId(UUID.randomUUID()), "Training", duration, autoStart, List.of(
                new Exercise.Strength(first, "Push-up", null, 1, 1, null, null),
                new Exercise.Cardio(second, "Run", null, 60, null, null)));
    }

    private static CycleDay.Rest rest() {
        return new CycleDay.Rest(new DayId(UUID.randomUUID()));
    }

    private static Program program(CycleDay... days) {
        // Exercises in different days are distinct even if the exercise parameters match.
        var unique = new java.util.HashSet<ExerciseId>();
        var normalized = new java.util.ArrayList<CycleDay>();
        for (var day : days) {
            if (day instanceof CycleDay.Work work && !work.exercises().stream().allMatch(e -> unique.add(e.id()))) {
                var exercises = work.exercises().stream().map(e -> e instanceof Exercise.Strength lift
                        ? (Exercise) new Exercise.Strength(new ExerciseId(UUID.randomUUID()), lift.name(), null,
                                1, 1, null, null)
                        : new Exercise.Cardio(new ExerciseId(UUID.randomUUID()), e.name(), null, 60, null, null)).toList();
                normalized.add(new CycleDay.Work(work.id(), work.name(), work.durationMinutes(), work.autoStart(), exercises));
            } else {
                normalized.add(day);
            }
        }
        return new Program(new ProgramId(UUID.randomUUID()), "Cycle", normalized);
    }

    private static List<DaySlot> slots(TrainingState state) {
        return state.run().phase().slots();
    }

    private static Instant time(String value) {
        return Instant.parse(value);
    }
}
