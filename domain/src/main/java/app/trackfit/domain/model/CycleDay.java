package app.trackfit.domain.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public sealed interface CycleDay permits CycleDay.Work, CycleDay.Rest {
    DayId id();

    record Work(DayId id, String name, int durationMinutes, LocalTime autoStart,
                List<Exercise> exercises) implements CycleDay {
        public Work {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            exercises = Collections.unmodifiableList(new ArrayList<>(exercises));
            exercises.forEach(Objects::requireNonNull);
        }
    }

    record Rest(DayId id) implements CycleDay {
        public Rest {
            Objects.requireNonNull(id);
        }
    }
}
