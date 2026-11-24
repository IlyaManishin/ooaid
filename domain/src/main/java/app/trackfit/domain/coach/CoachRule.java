package app.trackfit.domain.coach;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public interface CoachRule {
    String id();
    Optional<Intent> evaluate(CoachEvent event, CoachEngine.Context context);

    enum Priority { MOTIVATION, RESULT, SYSTEM }

    record Intent(String phrase, Priority priority, Set<String> tags, Set<String> suppresses,
                  Instant deliverAt) {
        public Intent(String phrase, Priority priority) {
            this(phrase, priority, Collections.emptySet(), Collections.emptySet(), null);
        }
    }
}
