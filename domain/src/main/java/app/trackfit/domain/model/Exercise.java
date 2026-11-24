package app.trackfit.domain.model;

import java.util.Objects;

public sealed interface Exercise permits Exercise.Strength, Exercise.Cardio, Exercise.Balance,
        Exercise.Stretch {
    enum Type { STRENGTH, CARDIO, BALANCE, STRETCH }
    enum BodySide { LEFT, RIGHT, BOTH }

    ExerciseId id();
    String name();
    String note();
    Type type();

    record Strength(ExerciseId id, String name, String note, int sets, int reps, Double weightKg,
                    Integer restSeconds) implements Exercise {
        public Strength {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }

        @Override
        public Type type() {
            return Type.STRENGTH;
        }
    }

    record Cardio(ExerciseId id, String name, String note, int durationSeconds, String intensity,
                  Integer targetPulse) implements Exercise {
        public Cardio {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }

        @Override
        public Type type() {
            return Type.CARDIO;
        }
    }

    record Balance(ExerciseId id, String name, String note, int durationSeconds, Integer sets,
                   BodySide side) implements Exercise {
        public Balance {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }

        @Override
        public Type type() {
            return Type.BALANCE;
        }
    }

    record Stretch(ExerciseId id, String name, String note, int durationSeconds, Integer sets,
                   BodySide side) implements Exercise {
        public Stretch {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
        }

        @Override
        public Type type() {
            return Type.STRETCH;
        }
    }
}
