package app.trackfit.domain.coach;

import static org.junit.Assert.*;

import app.trackfit.domain.engine.TrainingEngine;
import app.trackfit.domain.model.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

public class CoachEngineTest {
    private static final Instant START = Instant.parse("2026-11-24T08:00:00Z");
    private static final ZoneOffset ZONE = ZoneOffset.UTC;
    private static final CoachDefinition COACH = new CoachDefinition("one", "Coach", "", "", "", "", List.of());

    @Test
    public void restAutoStartAndReportsSelectDifferentPhrases() {
        var engine = engine(List.of(COACH), Map.of());
        var rest = engine.evaluate(new CoachEvent.AppEntered(), context(state(true), START, CoachMemory.empty()));
        assertEquals("rest_day[]", rest.line().text());
        var auto = TrainingEngine.reconcile(state(false), START, ZONE, date -> 0).state();
        var greeting = engine.evaluate(new CoachEvent.AppEntered(), context(auto, START, CoachMemory.empty()));
        assertEquals("auto_start[]", greeting.line().text());
        var report = new Report.Day(UUID.randomUUID(), START, false, START.atZone(ZONE).toLocalDate(),
                "Day", WorkState.Result.COMPLETED, 1, 1, true);
        assertEquals("day_completed[SAME_DAY_REPEAT]", engine.evaluate(new CoachEvent.ReportPresented(report),
                context(auto, START, CoachMemory.empty())).line().text());
        var incomplete = new Report.Day(report.id(), START, false, report.date(), "Day",
                WorkState.Result.INCOMPLETE, 1, 2, true);
        assertEquals("day_incomplete[SOFT]", engine.evaluate(new CoachEvent.ReportPresented(incomplete),
                context(auto, START, CoachMemory.empty())).line().text());
    }

    @Test
    public void motivationWaitsForTenQuietMinutesAndNeverRepeatsWithinSession() {
        var state = TrainingEngine.reconcile(state(false), START, ZONE, date -> 0).state();
        var engine = engine(List.of(COACH), Map.of());
        var afterFive = START.plusSeconds(300);
        var memory = new CoachMemory(afterFive, null);
        var afterTen = START.plusSeconds(600);
        assertNull(engine.evaluate(new CoachEvent.Tick(), context(state, afterTen, memory)).line());
        var afterFifteen = START.plusSeconds(900);
        var decision = engine.evaluate(new CoachEvent.Tick(), context(state, afterFifteen, memory));
        assertEquals("motivation[]", decision.line().text());
        assertEquals(afterFifteen.plusSeconds(10), decision.line().expiresAt());
        assertNull(engine.evaluate(new CoachEvent.Tick(),
                context(state, afterFifteen.plusSeconds(100), decision.memory())).line());
        assertFalse(CoachEngine.nextMotivationAt(context(TrainingState.empty(), afterFifteen, memory)).isPresent());
    }

    @Test
    public void registeredRuleAndCoachExtendEngineAndRespectSuppression() {
        var extra = new CoachDefinition("two", "Test", "", "", "", "", List.of("extra"));
        var added = new CoachRule() {
            @Override public String id() { return "extra"; }
            @Override public Optional<Intent> evaluate(CoachEvent event, CoachEngine.Context context) {
                return Optional.of(new Intent("custom", Priority.SYSTEM, Set.of(), Set.of("cycle_result"), null));
            }
        };
        var registry = new CoachRegistry(List.of(COACH, extra), StandardCoachRules.all(), Map.of("extra", added));
        var engine = new CoachEngine(registry, (id, key, tags) -> List.of(id + ":" + key), new Random(0));
        var report = new Report.Cycle(UUID.randomUUID(), START, false, 1, CycleOutcome.FULL_SUCCESS);
        var standard = context(state(true), START, CoachMemory.empty());
        assertEquals("one:cycle_success", engine.evaluate(new CoachEvent.ReportPresented(report), standard).line().text());
        var custom = CoachEngine.Context.at(START, ZONE, state(true), CoachMemory.empty(), extra);
        assertEquals("two:custom", engine.evaluate(new CoachEvent.ReportPresented(report), custom).line().text());
        assertEquals("two:custom", engine.evaluate(new CoachEvent() {}, custom).line().text());
    }

    private static CoachEngine engine(List<CoachDefinition> coaches, Map<String, CoachRule> extras) {
        return new CoachEngine(new CoachRegistry(coaches, StandardCoachRules.all(), extras),
                (id, key, tags) -> List.of(key + tags), new Random(0));
    }

    private static CoachEngine.Context context(TrainingState state, Instant when, CoachMemory memory) {
        return CoachEngine.Context.at(when, ZONE, state, memory, COACH);
    }

    private static TrainingState state(boolean restFirst) {
        var exercise = new Exercise.Strength(new ExerciseId(UUID.randomUUID()), "Lift", null, 1, 1, null, null);
        var work = new CycleDay.Work(new DayId(UUID.randomUUID()), "Day", 60, LocalTime.of(8, 0), List.of(exercise));
        var rest = new CycleDay.Rest(new DayId(UUID.randomUUID()));
        var program = new Program(new ProgramId(UUID.randomUUID()), "Program", List.of(work, rest));
        return TrainingEngine.startProgram(TrainingState.empty(), program, restFirst ? 1 : 0, UUID.randomUUID(),
                START.minusSeconds(1), ZONE, date -> 0).state();
    }
}
