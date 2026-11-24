package app.trackfit.domain.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoachRegistry {
    private final List<CoachDefinition> coaches;
    private final List<CoachRule> common;
    private final Map<String, CoachRule> extras;

    public CoachRegistry(List<CoachDefinition> coaches, List<CoachRule> common, Map<String, CoachRule> extras) {
        if (coaches.isEmpty()) throw new IllegalArgumentException("A default coach is required");
        this.coaches = Collections.unmodifiableList(new ArrayList<>(coaches));
        this.common = new ArrayList<>(common);
        this.extras = new LinkedHashMap<>(extras);
        for (CoachDefinition coach : coaches) rulesFor(coach);
    }

    public List<CoachDefinition> coaches() { return coaches; }

    public CoachDefinition selected(String id) {
        for (CoachDefinition coach : coaches) {
            if (coach.id().equals(id)) return coach;
        }
        return coaches.get(0);
    }

    public List<CoachRule> rulesFor(CoachDefinition coach) {
        List<CoachRule> rules = new ArrayList<>(common);
        for (String id : coach.extraRules()) {
            CoachRule rule = extras.get(id);
            if (rule == null) throw new IllegalArgumentException("Unregistered coach rule: " + id);
            rules.add(rule);
        }
        return rules;
    }
}
