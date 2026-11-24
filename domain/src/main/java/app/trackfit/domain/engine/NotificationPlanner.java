package app.trackfit.domain.engine;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.CyclePhase;
import app.trackfit.domain.model.DaySlot;
import app.trackfit.domain.model.Session;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class NotificationPlanner {
    public enum Kind { DAILY_REMINDER, AUTOSTART_LATE }
    public record PlannedNotification(String id, Kind kind, LocalDate date, Instant at, UUID runId, int cycle) {}

    private NotificationPlanner() {}

    public static List<PlannedNotification> plan(TrainingState state, boolean enabled, Instant now, ZoneId zone) {
        var run = state.run();
        if (!enabled || run == null || !(run.phase() instanceof CyclePhase.InProgress phase)) {
            return Collections.emptyList();
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate horizon = today.plusDays(7);
        List<PlannedNotification> plan = new ArrayList<>();
        for (DaySlot slot : phase.slots()) {
            if (slot.date().isBefore(today) || !slot.date().isBefore(horizon)
                    || !(slot.work() instanceof WorkState.NotStarted pending)) {
                continue;
            }
            var work = (CycleDay.Work) run.program().days().get(slot.dayIndex());
            addIfFuture(plan, run.id(), run.cycleNumber(), Kind.DAILY_REMINDER, slot.date(), LocalTime.NOON, now, zone);
            if (pending.autoStartEnabled() && work.autoStart() != null) {
                addIfFuture(plan, run.id(), run.cycleNumber(), Kind.AUTOSTART_LATE, slot.date(), work.autoStart(), now, zone);
            }
        }
        plan.sort(Comparator.comparing(PlannedNotification::at));
        return Collections.unmodifiableList(plan);
    }

    public static boolean shouldShow(PlannedNotification notification, TrainingState state, boolean enabled,
                                     boolean foreground, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        var run = state.run();
        if (!enabled || run == null || !run.id().equals(notification.runId())
                || run.cycleNumber() != notification.cycle() || now.isBefore(notification.at())
                || !today.equals(notification.date())) {
            return false;
        }
        for (DaySlot slot : run.phase().slots()) {
            if (!today.equals(slot.date())) continue;
            if (notification.kind() == Kind.DAILY_REMINDER) {
                return slot.work() instanceof WorkState.NotStarted;
            }
            return !foreground && slot.work() instanceof WorkState.Running running
                    && running.session().startKind() == Session.StartKind.AUTO
                    && running.session().startedAt().equals(notification.at());
        }
        return false;
    }

    private static void addIfFuture(List<PlannedNotification> plan, UUID runId, int cycle, Kind kind,
                                    LocalDate date, LocalTime time, Instant now, ZoneId zone) {
        Instant at = date.atTime(time).atZone(zone).toInstant();
        if (at.isAfter(now)) {
            plan.add(new PlannedNotification(runId + "." + cycle + "." + kind + "." + date,
                    kind, date, at, runId, cycle));
        }
    }
}
