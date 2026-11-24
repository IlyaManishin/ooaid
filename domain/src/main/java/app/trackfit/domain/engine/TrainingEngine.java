package app.trackfit.domain.engine;

import static app.trackfit.domain.engine.EngineResult.Rejection.*;

import app.trackfit.domain.engine.EngineResult.Rejection;
import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.CycleOutcome;
import app.trackfit.domain.model.CyclePhase;
import app.trackfit.domain.model.DaySlot;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramRun;
import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.Session;
import app.trackfit.domain.model.Session.CloseReason;
import app.trackfit.domain.model.Session.StartKind;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkState;
import app.trackfit.domain.model.WorkState.Result;
import app.trackfit.domain.model.WorkoutLogEntry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class TrainingEngine {
    public enum CycleDecision { SWITCH_PROGRAM, CONTINUE }

    private TrainingEngine() {}

    public static EngineResult reconcile(TrainingState state, Instant now, ZoneId zone, LogFacts facts) {
        return new Transition(state, now, zone, facts).result();
    }

    public static EngineResult startProgram(TrainingState state, Program program, int index, UUID runId,
                                            Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            if (!StartProgramPolicy.permits(transition.snapshot())) {
                transition.reject(ACTIVE_PROGRAM_EXISTS);
            } else if (!ProgramValidator.validate(program).isEmpty()) {
                transition.reject(INVALID_PROGRAM);
            } else if (index < 0 || index >= program.days().size()) {
                transition.reject(INVALID_START_DAY);
            } else {
                transition.run = openCycle(runId, program, index, 1, transition.today, now, zone);
            }
        });
    }

    public static EngineResult stopProgram(TrainingState state, Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            ProgramRun run = transition.run;
            if (run == null) {
                transition.reject(NO_ACTIVE_PROGRAM);
                return;
            }
            if (run.phase() instanceof CyclePhase.AwaitingDecision phase) {
                transition.viewReport(phase.reportId());
            }
            transition.run = null;
        });
    }

    public static EngineResult startWorkout(TrainingState state, Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            int index = transition.workIndex();
            if (index < 0) return;
            DaySlot slot = transition.slot(index);
            if (!(slot.work() instanceof WorkState.NotStarted pending)) {
                transition.reject(WORKOUT_ALREADY_STARTED);
                return;
            }
            Session session = openSession(transition.work(slot), slot.date(), now, StartKind.MANUAL, zone);
            transition.replaceSlot(index, slot.withWork(new WorkState.Running(pending.autoStartEnabled(), session)));
        });
    }

    public static EngineResult checkExercise(TrainingState state, ExerciseId exerciseId,
                                             Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            int index = transition.workIndex();
            if (index < 0) return;
            DaySlot slot = transition.slot(index);
            if (!(slot.work() instanceof WorkState.Running running)) {
                transition.reject(SESSION_NOT_RUNNING);
            } else if (transition.work(slot).exercises().stream().noneMatch(e -> e.id().equals(exerciseId))) {
                transition.reject(UNKNOWN_EXERCISE);
            } else if (running.session().checked().containsKey(exerciseId)) {
                transition.reject(ALREADY_CHECKED);
            } else {
                transition.replaceSlot(index, slot.withWork(new WorkState.Running(running.autoStartEnabled(),
                        running.session().withCheck(exerciseId, now))));
            }
        });
    }

    public static EngineResult finishWorkout(TrainingState state, Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            int index = transition.workIndex();
            if (index < 0) return;
            DaySlot slot = transition.slot(index);
            if (!(slot.work() instanceof WorkState.Running running)) {
                transition.reject(SESSION_NOT_RUNNING);
                return;
            }
            List<DaySlot> slots = new ArrayList<>(transition.run.phase().slots());
            slots.set(index, transition.close(slot, running.session(), now, CloseReason.MANUAL));
            transition.finishReconciliation(slots);
        });
    }

    public static EngineResult decideCycle(TrainingState state, CycleDecision decision,
                                           Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            ProgramRun run = transition.run;
            if (run == null || !(run.phase() instanceof CyclePhase.AwaitingDecision phase)) {
                transition.reject(CYCLE_NOT_AWAITING_DECISION);
                return;
            }
            if (decision == CycleDecision.CONTINUE) {
                if (now.isBefore(run.lastReconciledAt())) {
                    transition.reject(CLOCK_ROLLBACK);
                    return;
                }
                transition.run = openCycle(run.id(), run.program(), run.startDayIndex(), run.cycleNumber() + 1,
                        transition.today.plusDays(1), now, zone);
            } else {
                transition.events.add(new DomainEvent.ProgramSelectionRequested());
                transition.run = null;
            }
            transition.viewReport(phase.reportId());
        });
    }

    public static EngineResult markReportViewed(TrainingState state, UUID id, Instant now, ZoneId zone, LogFacts facts) {
        return command(state, now, zone, facts, transition -> {
            for (Report report : transition.reports) {
                if (!report.id().equals(id)) continue;
                if (report instanceof Report.Cycle && !report.viewed()) {
                    transition.reject(CYCLE_REPORT_REQUIRES_DECISION);
                } else {
                    transition.viewReport(id);
                }
                return;
            }
            transition.reject(REPORT_NOT_FOUND);
        });
    }

    public static Optional<Instant> nextTransitionAt(TrainingState state, ZoneId zone) {
        ProgramRun run = state.run();
        if (run == null || !(run.phase() instanceof CyclePhase.InProgress phase)) return Optional.empty();
        Instant next = null;
        Instant reconciledAt = run.lastReconciledAt();
        for (DaySlot slot : phase.slots()) {
            next = earlierFuture(next, midnight(slot.date(), zone), reconciledAt);
            if (slot.work() instanceof WorkState.Running running) {
                next = earlierFuture(next, running.session().deadlineAt(), reconciledAt);
            } else if (slot.work() instanceof WorkState.NotStarted pending) {
                CycleDay.Work work = (CycleDay.Work) run.program().days().get(slot.dayIndex());
                if (pending.autoStartEnabled() && work.autoStart() != null) {
                    next = earlierFuture(next, autoStart(slot.date(), work, zone), reconciledAt);
                }
                next = earlierFuture(next, midnight(slot.date().plusDays(1), zone), reconciledAt);
            } else if (slot.work() == null) {
                next = earlierFuture(next, midnight(slot.date().plusDays(1), zone), reconciledAt);
            }
        }
        return Optional.ofNullable(next);
    }

    private static EngineResult command(TrainingState state, Instant now, ZoneId zone, LogFacts facts,
                                        Consumer<Transition> action) {
        Transition transition = new Transition(state, now, zone, facts);
        action.accept(transition);
        return transition.result();
    }

    private static ProgramRun openCycle(UUID runId, Program program, int startIndex, int number,
                                        LocalDate date, Instant now, ZoneId zone) {
        List<DaySlot> slots = new ArrayList<>();
        int dayIndex = startIndex;
        for (int offset = 0; offset < program.days().size(); offset++) {
            LocalDate slotDate = date.plusDays(offset);
            WorkState state = null;
            if (program.days().get(dayIndex) instanceof CycleDay.Work work) {
                boolean autoEnabled = work.autoStart() != null && autoStart(slotDate, work, zone).isAfter(now);
                state = new WorkState.NotStarted(autoEnabled);
            }
            slots.add(new DaySlot(slotDate, dayIndex, state));
            dayIndex = (dayIndex + 1) % program.days().size();
        }
        return new ProgramRun(runId, program, startIndex, number, new CyclePhase.InProgress(slots), now);
    }

    private static Session openSession(CycleDay.Work work, LocalDate date, Instant start,
                                       StartKind kind, ZoneId zone) {
        Instant midnight = midnight(date.plusDays(1), zone);
        Instant durationEnd = start.plus(work.durationMinutes(), ChronoUnit.MINUTES);
        boolean beforeMidnight = durationEnd.isBefore(midnight);
        return new Session(start, kind, beforeMidnight ? durationEnd : midnight,
                beforeMidnight ? CloseReason.DEADLINE : CloseReason.MIDNIGHT, Collections.emptyMap());
    }

    private static Instant midnight(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static Instant autoStart(LocalDate date, CycleDay.Work work, ZoneId zone) {
        return date.atTime(work.autoStart()).atZone(zone).toInstant();
    }

    private static Instant earlierFuture(Instant first, Instant candidate, Instant after) {
        return candidate.isAfter(after) && (first == null || candidate.isBefore(first)) ? candidate : first;
    }

    private static final class Transition {
        final Instant now;
        final ZoneId zone;
        final LocalDate today;
        final LogFacts facts;
        final List<Report> reports;
        final List<WorkoutLogEntry> log = new ArrayList<>();
        final List<DomainEvent> events = new ArrayList<>();
        ProgramRun run;
        Rejection rejection;

        Transition(TrainingState state, Instant now, ZoneId zone, LogFacts facts) {
            this.now = now;
            this.zone = zone;
            this.today = now.atZone(zone).toLocalDate();
            this.facts = facts;
            this.run = state.run();
            this.reports = new ArrayList<>(state.reports());
            if (run != null && !now.isBefore(run.lastReconciledAt())) {
                reconcileRun();
            }
        }

        private void reconcileRun() {
            if (!(run.phase() instanceof CyclePhase.InProgress inProgress)) return;
            List<DaySlot> slots = new ArrayList<>(inProgress.slots().size());
            for (DaySlot slot : inProgress.slots()) {
                slots.add(reconcileSlot(slot));
            }
            finishReconciliation(slots);
        }

        private DaySlot reconcileSlot(DaySlot slot) {
            if (slot.work() instanceof WorkState.NotStarted pending && !slot.date().isAfter(today)) {
                CycleDay.Work work = work(slot);
                if (pending.autoStartEnabled() && work.autoStart() != null) {
                    Instant start = autoStart(slot.date(), work, zone);
                    if (!start.isAfter(now)) {
                        Session session = openSession(work, slot.date(), start, StartKind.AUTO, zone);
                        slot = slot.withWork(new WorkState.Running(true, session));
                    }
                }
                if (slot.work() instanceof WorkState.NotStarted && slot.date().isBefore(today)) {
                    return close(slot, null, midnight(slot.date().plusDays(1), zone), CloseReason.MIDNIGHT);
                }
            }
            if (slot.work() instanceof WorkState.Running running && !now.isBefore(running.session().deadlineAt())) {
                return close(slot, running.session(), running.session().deadlineAt(), running.session().timeoutReason());
            }
            return slot;
        }

        DaySlot close(DaySlot slot, Session session, Instant at, CloseReason reason) {
            CycleDay.Work workout = work(slot);
            int total = workout.exercises().size();
            int count = session == null ? 0 : session.checked().size();
            Result result = count == 0 ? Result.SKIPPED : count == total ? Result.COMPLETED : Result.INCOMPLETE;
            boolean earlierSuccess = facts.fullCompletionsOn(slot.date()) > 0 || log.stream().anyMatch(
                    entry -> entry.date().equals(slot.date()) && entry.result() == Result.COMPLETED);
            UUID id = eventId("day." + slot.dayIndex());
            List<WorkoutLogEntry.ExerciseResult> exercises = new ArrayList<>(total);
            for (var exercise : workout.exercises()) {
                exercises.add(new WorkoutLogEntry.ExerciseResult(exercise.name(), exercise.type(),
                        session != null && session.checked().containsKey(exercise.id())));
            }
            WorkoutLogEntry entry = new WorkoutLogEntry(id, slot.date(), run.program().name(), workout.name(), result,
                    reason, session == null ? null : session.startedAt(), at, run.cycleNumber(), exercises);
            log.add(entry);
            events.add(new DomainEvent.WorkoutClosed(entry));
            reports.add(new Report.Day(id, at, false, slot.date(), workout.name(), result, count, total, earlierSuccess));
            return slot.withWork(new WorkState.Closed(slot.work().autoStartEnabled(), result, session, at, reason));
        }

        void finishReconciliation(List<DaySlot> slots) {
            int workCount = 0;
            int completed = 0;
            int skipped = 0;
            Instant cycleClosedAt = Instant.MIN;
            boolean finished = true;
            for (DaySlot slot : slots) {
                if (slot.work() == null) {
                    Instant midnight = midnight(slot.date().plusDays(1), zone);
                    finished &= !now.isBefore(midnight);
                    if (cycleClosedAt.isBefore(midnight)) cycleClosedAt = midnight;
                } else if (slot.work() instanceof WorkState.Closed closed) {
                    workCount++;
                    if (closed.result() == Result.COMPLETED) completed++;
                    if (closed.result() == Result.SKIPPED) skipped++;
                    if (cycleClosedAt.isBefore(closed.closedAt())) cycleClosedAt = closed.closedAt();
                } else {
                    finished = false;
                }
            }
            if (!finished) {
                run = run.withPhase(new CyclePhase.InProgress(slots), now);
                return;
            }
            CycleOutcome outcome = completed == workCount ? CycleOutcome.FULL_SUCCESS
                    : skipped == workCount ? CycleOutcome.FULL_FAILURE : CycleOutcome.PARTIAL_SUCCESS;
            DaySlot finalSlot = slots.get(slots.size() - 1);
            if (finalSlot.work() instanceof WorkState.Closed closed && closed.closedAt().equals(cycleClosedAt)) {
                UUID replaced = eventId("day." + finalSlot.dayIndex());
                reports.removeIf(report -> report.id().equals(replaced) && !report.viewed());
            }
            Report.Cycle report = new Report.Cycle(eventId("cycle"), cycleClosedAt, false, run.cycleNumber(), outcome);
            reports.add(report);
            events.add(new DomainEvent.CycleCompleted(report));
            run = run.withPhase(new CyclePhase.AwaitingDecision(slots, outcome, report.id()), now);
        }

        CycleDay.Work work(DaySlot slot) {
            return (CycleDay.Work) run.program().days().get(slot.dayIndex());
        }

        DaySlot slot(int index) {
            return run.phase().slots().get(index);
        }

        int workIndex() {
            if (run == null) {
                reject(NO_ACTIVE_PROGRAM);
            } else if (now.isBefore(run.lastReconciledAt())) {
                reject(CLOCK_ROLLBACK);
            } else if (run.phase() instanceof CyclePhase.InProgress phase) {
                List<DaySlot> slots = phase.slots();
                for (int i = 0; i < slots.size(); i++) {
                    DaySlot slot = slots.get(i);
                    if (today.equals(slot.date()) && slot.work() != null) return i;
                }
                reject(NOT_TODAY);
            } else {
                reject(SESSION_NOT_RUNNING);
            }
            return -1;
        }

        void replaceSlot(int index, DaySlot replacement) {
            List<DaySlot> slots = new ArrayList<>(run.phase().slots());
            slots.set(index, replacement);
            run = run.withPhase(new CyclePhase.InProgress(slots), now);
        }

        void viewReport(UUID id) {
            for (int i = 0; i < reports.size(); i++) {
                Report report = reports.get(i);
                if (report.id().equals(id)) {
                    reports.set(i, report.markViewed());
                    break;
                }
            }
        }

        void reject(Rejection failure) {
            rejection = failure;
        }

        UUID eventId(String name) {
            String key = run.id() + ":" + run.cycleNumber() + ":" + name;
            return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        }

        TrainingState snapshot() {
            return new TrainingState(run, reports);
        }

        EngineResult result() {
            reports.sort(java.util.Comparator.comparing(Report::createdAt));
            return new EngineResult(snapshot(), log, events, rejection);
        }
    }
}
