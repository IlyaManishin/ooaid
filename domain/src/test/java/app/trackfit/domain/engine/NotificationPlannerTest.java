package app.trackfit.domain.engine;

import static app.trackfit.domain.engine.NotificationPlanner.Kind.*;
import static org.junit.Assert.*;

import app.trackfit.domain.model.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class NotificationPlannerTest {
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final Instant MORNING = Instant.parse("2026-11-24T08:00:00Z");
    private static final Instant NOON = Instant.parse("2026-11-24T12:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-11-24T18:30:00Z");
    private static final LogFacts NO_LOG = date -> 0;

    @Test
    public void planOffersNoonAndAutoStartOnlyForEligibleFutureEvents() {
        var state = start(MORNING);
        var plan = NotificationPlanner.plan(state, true, MORNING, UTC);
        assertEquals(List.of(DAILY_REMINDER, AUTOSTART_LATE), plan.stream().map(NotificationPlanner.PlannedNotification::kind).toList());
        assertEquals(List.of(NOON, EVENING), plan.stream().map(NotificationPlanner.PlannedNotification::at).toList());
        assertTrue(NotificationPlanner.plan(state, false, MORNING, UTC).isEmpty());
        assertEquals(List.of(DAILY_REMINDER), NotificationPlanner.plan(start(EVENING), true,
                MORNING.minusSeconds(3600), UTC).stream().map(NotificationPlanner.PlannedNotification::kind).toList());
        assertTrue(NotificationPlanner.plan(state, true, EVENING.plusSeconds(1), UTC).isEmpty());
    }

    @Test
    public void delayedRemindersCheckCurrentDayAndActualWorkoutState() {
        var state = start(MORNING);
        var reminder = NotificationPlanner.plan(state, true, MORNING, UTC).get(0);
        assertTrue(NotificationPlanner.shouldShow(reminder, state, true, true, NOON, UTC));
        var manuallyStarted = TrainingEngine.startWorkout(state, NOON, UTC, NO_LOG).state();
        assertFalse(NotificationPlanner.shouldShow(reminder, manuallyStarted, true, true, NOON, UTC));
        assertFalse(NotificationPlanner.shouldShow(reminder, state, false, false, NOON, UTC));
        assertFalse(NotificationPlanner.shouldShow(reminder, state, true, false, NOON.plusSeconds(86400), UTC));
        assertFalse(NotificationPlanner.shouldShow(reminder, start(MORNING), true, false, NOON, UTC));
    }

    @Test
    public void autoStartAlertNeedsMatchingBackgroundSession() {
        var state = start(MORNING);
        var reminder = NotificationPlanner.plan(state, true, MORNING, UTC).get(1);
        var running = TrainingEngine.reconcile(state, EVENING, UTC, NO_LOG).state();
        assertTrue(NotificationPlanner.shouldShow(reminder, running, true, false, EVENING, UTC));
        assertFalse(NotificationPlanner.shouldShow(reminder, running, true, true, EVENING, UTC));
        var finished = TrainingEngine.reconcile(running, EVENING.plusSeconds(1800), UTC, NO_LOG).state();
        assertFalse(NotificationPlanner.shouldShow(reminder, finished, true, false, EVENING.plusSeconds(1800), UTC));
    }

    private static TrainingState start(Instant when) {
        var exercise = new Exercise.Strength(new ExerciseId(UUID.randomUUID()), "Exercise", null, 1, 1, null, null);
        var work = new CycleDay.Work(new DayId(UUID.randomUUID()), "Day", 30, LocalTime.of(18, 30), List.of(exercise));
        var rest = new CycleDay.Rest(new DayId(UUID.randomUUID()));
        var program = new Program(new ProgramId(UUID.randomUUID()), "Program", List.of(work, rest));
        return TrainingEngine.startProgram(TrainingState.empty(), program, 0, UUID.randomUUID(), when, UTC, NO_LOG).state();
    }
}
