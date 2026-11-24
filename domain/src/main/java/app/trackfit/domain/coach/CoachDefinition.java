package app.trackfit.domain.coach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CoachDefinition(String id, String displayName, String character, String bio,
                              String preview, String avatar, List<String> extraRules) {
    public CoachDefinition {
        extraRules = Collections.unmodifiableList(new ArrayList<>(extraRules));
    }
}
