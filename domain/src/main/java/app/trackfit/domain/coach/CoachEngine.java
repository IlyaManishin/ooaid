package app.trackfit.domain.coach;

import app.trackfit.domain.model.DaySlot;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkState;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class CoachEngine {
    public static final Duration DISPLAY_TIME = Duration.ofSeconds(10);
    public record Line(String coachId, String text, Instant expiresAt, CoachRule.Priority priority) {}
    public record Decision(CoachMemory memory, Line line) {}

    public record Context(Instant now, LocalDate today, TrainingState training, DaySlot slot, CoachMemory memory,
                          CoachDefinition coach) {
        public static Context at(Instant now, ZoneId zone, TrainingState state, CoachMemory memory,
                                 CoachDefinition coach) {
            LocalDate date = now.atZone(zone).toLocalDate();
            DaySlot current = null;
            if (state.run() != null) {
                for (DaySlot slot : state.run().phase().slots()) {
                    if (slot.date().equals(date)) current = slot;
                }
            }
            return new Context(now, date, state, current, memory, coach);
        }

        public String sessionKey() {
            if (slot == null || !(slot.work() instanceof WorkState.Running running)) return null;
            return training.run().id() + "/" + training.run().cycleNumber() + "/" + slot.dayIndex()
                    + "/" + running.session().startedAt();
        }
    }

    @FunctionalInterface
    public interface PhraseBank {
        List<String> phrases(String coachId, String key, Set<String> tags);
    }

    private record Candidate(String rule, CoachRule.Intent intent) {}

    private final CoachRegistry registry;
    private final PhraseBank bank;
    private final Random random;

    public CoachEngine(CoachRegistry registry, PhraseBank bank, Random random) {
        this.registry = registry;
        this.bank = bank;
        this.random = random;
    }

    public Decision evaluate(CoachEvent event, Context context) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> suppressed = new HashSet<>();
        for (CoachRule rule : registry.rulesFor(context.coach())) {
            rule.evaluate(event, context).ifPresent(intent -> {
                if (intent.deliverAt() == null || !intent.deliverAt().isAfter(context.now())) {
                    candidates.add(new Candidate(rule.id(), intent));
                    suppressed.addAll(intent.suppresses());
                }
            });
        }
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (!suppressed.contains(candidate.rule()) && (best == null
                    || candidate.intent().priority().compareTo(best.intent().priority()) > 0)) best = candidate;
        }
        if (best == null) return new Decision(context.memory(), null);
        var intent = best.intent();
        List<String> phrases = bank.phrases(context.coach().id(), intent.phrase(), intent.tags());
        if (phrases.isEmpty()) return new Decision(context.memory(), null);
        String chosen = phrases.get(random.nextInt(phrases.size()));
        String motivated = intent.priority() == CoachRule.Priority.MOTIVATION
                ? context.sessionKey() : context.memory().motivatedSession();
        return new Decision(new CoachMemory(context.now(), motivated),
                new Line(context.coach().id(), chosen, context.now().plus(DISPLAY_TIME), intent.priority()));
    }

    public static Optional<Instant> nextMotivationAt(Context context) {
        String session = context.sessionKey();
        if (session == null || session.equals(context.memory().motivatedSession())) return Optional.empty();
        var running = (WorkState.Running) context.slot().work();
        Instant at = running.session().startedAt().plus(Duration.ofMinutes(10));
        if (context.memory().lastLineAt() != null) {
            Instant afterLastLine = context.memory().lastLineAt().plus(Duration.ofMinutes(10));
            if (afterLastLine.isAfter(at)) at = afterLastLine;
        }
        return at.isBefore(running.session().deadlineAt()) && context.now().isBefore(running.session().deadlineAt())
                ? Optional.of(at) : Optional.empty();
    }
}
