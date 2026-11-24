package app.trackfit.domain.controller;

import app.trackfit.domain.coach.CoachDefinition;
import app.trackfit.domain.coach.CoachEngine;
import app.trackfit.domain.coach.CoachEvent;
import app.trackfit.domain.coach.CoachMemory;
import app.trackfit.domain.coach.CoachRegistry;
import app.trackfit.domain.coach.CoachRule;
import app.trackfit.domain.engine.DomainEvent;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.ports.CoachMemoryStore;
import app.trackfit.domain.ports.SettingsRepository;
import app.trackfit.domain.ports.TimeSource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class CoachController {
    public record Rules(CoachRegistry registry, CoachEngine engine) {}
    public record State(List<CoachDefinition> coaches, CoachDefinition selected, CoachEngine.Line line,
                        Instant nextMotivationAt) {}

    private final Supplier<Rules> loadRules;
    private final CoachMemoryStore store;
    private final SettingsRepository settings;
    private final TimeSource time;
    private final Executor writer;
    private final StatePublisher<State> published = new StatePublisher<>(
            new State(Collections.emptyList(), null, null, null));
    private TrainingState training = TrainingState.empty();
    private Rules rules;
    private CoachMemory memory;
    private CoachEngine.Line line;
    private UUID lastPresented;

    public CoachController(Supplier<Rules> loadRules, CoachMemoryStore store, SettingsRepository settings,
                           TimeSource time, Executor writer) {
        this.loadRules = loadRules;
        this.store = store;
        this.settings = settings;
        this.time = time;
        this.writer = writer;
    }

    public Observation<State> state() { return published; }

    public void initialize(TrainingController trainingController, DomainEvents events) {
        writer.execute(this::publish);
        events.subscribe(event -> {
            if (event instanceof DomainEvent.WorkoutClosed && line != null
                    && line.priority() == CoachRule.Priority.MOTIVATION) {
                line = null;
                publish();
            }
        });
        trainingController.state().subscribe(value -> writer.execute(() -> {
            training = value;
            publish();
        }));
    }

    public void appEntered(TrainingState state) {
        writer.execute(() -> {
            training = state;
            evaluate(new CoachEvent.AppEntered());
        });
    }

    public void reportPresented(UUID id) {
        writer.execute(() -> {
            if (id.equals(lastPresented)) return;
            for (var report : training.reports()) {
                if (report.id().equals(id) && !report.viewed()) {
                    lastPresented = id;
                    evaluate(new CoachEvent.ReportPresented(report));
                    return;
                }
            }
        });
    }

    public void tick() { writer.execute(() -> evaluate(new CoachEvent.Tick())); }

    public void dismiss() {
        writer.execute(() -> {
            line = null;
            publish();
        });
    }

    public void select(String id) {
        writer.execute(() -> {
            ensureLoaded();
            CoachDefinition selected = rules.registry().selected(id);
            settings.setSelectedCoachId(selected.id());
            line = null;
            publish();
        });
    }

    private void evaluate(CoachEvent event) {
        var context = context();
        var decision = rules.engine().evaluate(event, context);
        if (decision.line() != null) {
            if (line != null && line.expiresAt().isAfter(context.now())
                    && event instanceof CoachEvent.Tick
                    && line.priority().compareTo(decision.line().priority()) > 0) {
                return;
            }
            store.saveCoachMemory(decision.memory());
            memory = decision.memory();
            line = decision.line();
        }
        publish();
    }

    private CoachEngine.Context context() {
        ensureLoaded();
        return CoachEngine.Context.at(time.now(), time.zone(), training, memory,
                rules.registry().selected(settings.selectedCoachId()));
    }

    private void ensureLoaded() {
        if (rules == null) {
            memory = store.loadCoachMemory();
            rules = loadRules.get();
        }
    }

    private void publish() {
        var context = context();
        if (line != null && !line.expiresAt().isAfter(context.now())) line = null;
        published.update(new State(rules.registry().coaches(), context.coach(), line,
                CoachEngine.nextMotivationAt(context).orElse(null)));
    }
}
