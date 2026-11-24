package app.trackfit.domain.engine;

import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.Exercise.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ExerciseSpecs {
    public enum Field {
        NAME, SETS, REPS, WEIGHT_KG, REST_SECONDS, DURATION_SECONDS, INTENSITY, TARGET_PULSE, SIDE, NOTE
    }

    public record Spec(List<Field> required, List<Field> optional, List<Field> summary) {}

    private ExerciseSpecs() {}

    public static Spec forType(Type type) {
        return Specs.valueOf(type.name()).spec;
    }

    public static Object value(Exercise exercise, Field field) {
        return switch (field) {
            case NAME -> exercise.name();
            case NOTE -> exercise.note();
            case SETS -> switch (exercise.type()) {
                case STRENGTH -> ((Exercise.Strength) exercise).sets();
                case BALANCE -> ((Exercise.Balance) exercise).sets();
                case STRETCH -> ((Exercise.Stretch) exercise).sets();
                case CARDIO -> null;
            };
            case REPS -> ((Exercise.Strength) exercise).reps();
            case WEIGHT_KG -> ((Exercise.Strength) exercise).weightKg();
            case REST_SECONDS -> ((Exercise.Strength) exercise).restSeconds();
            case DURATION_SECONDS -> switch (exercise.type()) {
                case CARDIO -> ((Exercise.Cardio) exercise).durationSeconds();
                case BALANCE -> ((Exercise.Balance) exercise).durationSeconds();
                case STRETCH -> ((Exercise.Stretch) exercise).durationSeconds();
                case STRENGTH -> null;
            };
            case INTENSITY -> ((Exercise.Cardio) exercise).intensity();
            case TARGET_PULSE -> ((Exercise.Cardio) exercise).targetPulse();
            case SIDE -> exercise instanceof Exercise.Balance balance
                    ? balance.side() : ((Exercise.Stretch) exercise).side();
        };
    }

    private enum Specs {
        STRENGTH(fields(Field.NAME, Field.SETS, Field.REPS),
                fields(Field.WEIGHT_KG, Field.REST_SECONDS, Field.NOTE),
                fields(Field.NAME, Field.SETS, Field.REPS, Field.WEIGHT_KG)),
        CARDIO(fields(Field.NAME, Field.DURATION_SECONDS),
                fields(Field.INTENSITY, Field.TARGET_PULSE, Field.NOTE),
                fields(Field.NAME, Field.DURATION_SECONDS, Field.INTENSITY)),
        BALANCE(fields(Field.NAME, Field.DURATION_SECONDS),
                fields(Field.SETS, Field.SIDE, Field.NOTE),
                fields(Field.NAME, Field.DURATION_SECONDS, Field.SETS)),
        STRETCH(BALANCE.spec.required(), BALANCE.spec.optional(), BALANCE.spec.summary());

        final Spec spec;

        Specs(List<Field> required, List<Field> optional, List<Field> summary) {
            spec = new Spec(required, optional, summary);
        }
    }

    private static List<Field> fields(Field... fields) {
        return Collections.unmodifiableList(Arrays.asList(fields));
    }
}
