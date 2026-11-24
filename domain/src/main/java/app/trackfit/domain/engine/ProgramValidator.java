package app.trackfit.domain.engine;

import app.trackfit.domain.engine.ExerciseSpecs.Field;
import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProgramValidator {
    public enum Code {
        PROGRAM_NAME, EMPTY_CYCLE, NO_WORK_DAY, DUPLICATE_DAY, DAY_NAME, DAY_DURATION,
        EMPTY_WORK_DAY, DUPLICATE_EXERCISE, EXERCISE_FIELD
    }

    public record Issue(Code code, DayId dayId, ExerciseId exerciseId, Field field) {}

    private ProgramValidator() {}

    public static List<Issue> validate(Program program) {
        List<Issue> issues = new ArrayList<>();
        if (blank(program.name())) {
            issues.add(new Issue(Code.PROGRAM_NAME, null, null, null));
        }
        if (program.days().isEmpty()) {
            issues.add(new Issue(Code.EMPTY_CYCLE, null, null, null));
        }

        boolean hasWorkDay = false;
        Set<DayId> days = new HashSet<>();
        Set<ExerciseId> exercises = new HashSet<>();
        for (CycleDay day : program.days()) {
            if (!days.add(day.id())) {
                issues.add(new Issue(Code.DUPLICATE_DAY, day.id(), null, null));
            }
            if (day instanceof CycleDay.Work work) {
                hasWorkDay = true;
                if (blank(work.name())) {
                    issues.add(new Issue(Code.DAY_NAME, work.id(), null, null));
                }
                if (work.durationMinutes() < 1) {
                    issues.add(new Issue(Code.DAY_DURATION, work.id(), null, null));
                }
                if (work.exercises().isEmpty()) {
                    issues.add(new Issue(Code.EMPTY_WORK_DAY, work.id(), null, null));
                }
                for (Exercise exercise : work.exercises()) {
                    if (!exercises.add(exercise.id())) {
                        issues.add(new Issue(Code.DUPLICATE_EXERCISE, work.id(), exercise.id(), null));
                    }
                    for (Field field : invalidFields(exercise)) {
                        issues.add(new Issue(Code.EXERCISE_FIELD, work.id(), exercise.id(), field));
                    }
                }
            }
        }
        if (!hasWorkDay) {
            issues.add(new Issue(Code.NO_WORK_DAY, null, null, null));
        }
        return Collections.unmodifiableList(issues);
    }

    public static List<Field> invalidFields(Exercise exercise) {
        var spec = ExerciseSpecs.forType(exercise.type());
        List<Field> invalid = new ArrayList<>();
        for (Field field : spec.required()) {
            Object value = ExerciseSpecs.value(exercise, field);
            boolean valid = value instanceof String text ? !blank(text) : ((Integer) value) > 0;
            if (!valid) {
                invalid.add(field);
            }
        }
        for (Field field : spec.optional()) {
            Object value = ExerciseSpecs.value(exercise, field);
            if (value != null && !validOptional(field, value)) {
                invalid.add(field);
            }
        }
        return Collections.unmodifiableList(invalid);
    }

    private static boolean validOptional(Field field, Object value) {
        return switch (field) {
            case WEIGHT_KG -> Double.isFinite((Double) value) && (Double) value >= 0;
            case REST_SECONDS -> (Integer) value >= 0;
            case SETS, TARGET_PULSE -> (Integer) value > 0;
            default -> true;
        };
    }

    private static boolean blank(String value) {
        return value.codePoints().allMatch(Character::isWhitespace);
    }
}
