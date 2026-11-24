package app.trackfit.infra.mapping;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.infra.db.Rows;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class ProgramRows {
    private ProgramRows() {}

    public static Program read(Rows.ProgramRow row, List<Rows.DayRow> days,
                               Function<String, List<Rows.ExerciseRow>> exercises) {
        List<CycleDay> cycle = new ArrayList<>();
        for (Rows.DayRow day : days) {
            DayId id = new DayId(UUID.fromString(day.id));
            if ("REST".equals(day.kind)) {
                cycle.add(new CycleDay.Rest(id));
            } else if ("WORK".equals(day.kind)) {
                List<Exercise> workout = new ArrayList<>();
                for (Rows.ExerciseRow exercise : exercises.apply(day.id)) {
                    workout.add(readExercise(exercise));
                }
                LocalTime autoStart = day.autoStartMinute == null ? null
                        : LocalTime.ofSecondOfDay(day.autoStartMinute * 60L);
                cycle.add(new CycleDay.Work(id, day.name, day.durationMinutes, autoStart, workout));
            } else {
                throw new IllegalArgumentException("Unknown cycle day kind: " + day.kind);
            }
        }
        return new Program(new ProgramId(UUID.fromString(row.id)), row.name, cycle);
    }

    public static Rows.ProgramRow toRow(Program program) {
        Rows.ProgramRow row = new Rows.ProgramRow();
        row.id = program.id().value().toString();
        row.name = program.name();
        return row;
    }

    public static Rows.DayRow toRow(CycleDay day, String programId, int position) {
        Rows.DayRow row = new Rows.DayRow();
        row.programId = programId;
        row.position = position;
        row.id = day.id().value().toString();
        if (day instanceof CycleDay.Work work) {
            row.kind = "WORK";
            row.name = work.name();
            row.durationMinutes = work.durationMinutes();
            row.autoStartMinute = work.autoStart() == null ? null : work.autoStart().toSecondOfDay() / 60;
        } else {
            row.kind = "REST";
        }
        return row;
    }

    public static Rows.ExerciseRow toRow(Exercise exercise, String dayId, int position) {
        Rows.ExerciseRow row = new Rows.ExerciseRow();
        row.id = exercise.id().value().toString();
        row.dayId = dayId;
        row.position = position;
        row.type = exercise.type().name();
        row.name = exercise.name();
        row.note = exercise.note();
        if (exercise instanceof Exercise.Strength strength) {
            row.sets = strength.sets();
            row.reps = strength.reps();
            row.weight = strength.weightKg();
            row.rest = strength.restSeconds();
        } else if (exercise instanceof Exercise.Cardio cardio) {
            row.duration = cardio.durationSeconds();
            row.intensity = cardio.intensity();
            row.pulse = cardio.targetPulse();
        } else if (exercise instanceof Exercise.Balance balance) {
            row.duration = balance.durationSeconds();
            row.sets = balance.sets();
            row.side = balance.side() == null ? null : balance.side().name();
        } else if (exercise instanceof Exercise.Stretch stretch) {
            row.duration = stretch.durationSeconds();
            row.sets = stretch.sets();
            row.side = stretch.side() == null ? null : stretch.side().name();
        }
        return row;
    }

    public static Exercise readExercise(Rows.ExerciseRow row) {
        ExerciseId id = new ExerciseId(UUID.fromString(row.id));
        Exercise.BodySide side = row.side == null ? null : Exercise.BodySide.valueOf(row.side);
        return switch (Exercise.Type.valueOf(row.type)) {
            case STRENGTH -> new Exercise.Strength(id, row.name, row.note, row.sets, row.reps, row.weight, row.rest);
            case CARDIO -> new Exercise.Cardio(id, row.name, row.note, row.duration, row.intensity, row.pulse);
            case BALANCE -> new Exercise.Balance(id, row.name, row.note, row.duration, row.sets, side);
            case STRETCH -> new Exercise.Stretch(id, row.name, row.note, row.duration, row.sets, side);
        };
    }
}
