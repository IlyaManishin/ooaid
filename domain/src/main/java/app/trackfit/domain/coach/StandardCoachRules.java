package app.trackfit.domain.coach;

import app.trackfit.domain.coach.CoachRule.Intent;
import app.trackfit.domain.coach.CoachRule.Priority;
import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.Session;
import app.trackfit.domain.model.WorkState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public final class StandardCoachRules {
    private StandardCoachRules() {}

    public static List<CoachRule> all() {
        return Arrays.asList(
                new Rule("auto_start", (event, context) -> event instanceof CoachEvent.AppEntered
                        && context.slot() != null && context.slot().work() instanceof WorkState.Running running
                        && running.session().startKind() == Session.StartKind.AUTO
                        ? new Intent("auto_start", Priority.SYSTEM) : null),
                new Rule("rest_day", (event, context) -> event instanceof CoachEvent.AppEntered
                        && context.slot() != null && context.slot().work() == null
                        ? new Intent("rest_day", Priority.SYSTEM) : null),
                new Rule("day_result", StandardCoachRules::dayResult),
                new Rule("cycle_result", StandardCoachRules::cycleResult),
                new Rule("motivation", (event, context) -> event instanceof CoachEvent.Tick
                        ? CoachEngine.nextMotivationAt(context).map(at -> new Intent("motivation", Priority.MOTIVATION,
                                Collections.emptySet(), Collections.emptySet(), at)).orElse(null) : null));
    }

    private static Intent dayResult(CoachEvent event, CoachEngine.Context context) {
        if (!(event instanceof CoachEvent.ReportPresented shown) || !(shown.report() instanceof Report.Day day)) {
            return null;
        }
        return switch (day.result()) {
            case COMPLETED -> new Intent("day_completed", Priority.RESULT,
                    day.earlierSuccess() ? Collections.singleton("SAME_DAY_REPEAT") : Collections.emptySet(),
                    Collections.emptySet(), null);
            case INCOMPLETE -> new Intent("day_incomplete", Priority.RESULT,
                    day.earlierSuccess() ? Collections.singleton("SOFT") : Collections.emptySet(),
                    Collections.emptySet(), null);
            case SKIPPED -> new Intent("day_skipped", Priority.RESULT);
        };
    }

    private static Intent cycleResult(CoachEvent event, CoachEngine.Context context) {
        if (!(event instanceof CoachEvent.ReportPresented shown) || !(shown.report() instanceof Report.Cycle cycle)) {
            return null;
        }
        String key = switch (cycle.outcome()) {
            case FULL_SUCCESS -> "cycle_success";
            case PARTIAL_SUCCESS -> "cycle_partial";
            case FULL_FAILURE -> "cycle_failure";
        };
        return new Intent(key, Priority.RESULT, Collections.emptySet(), Collections.singleton("day_result"), null);
    }

    private record Rule(String id, BiFunction<CoachEvent, CoachEngine.Context, Intent> rule) implements CoachRule {
        @Override
        public Optional<Intent> evaluate(CoachEvent event, CoachEngine.Context context) {
            return Optional.ofNullable(rule.apply(event, context));
        }
    }
}
