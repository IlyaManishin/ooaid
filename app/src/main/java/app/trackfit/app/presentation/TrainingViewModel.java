package app.trackfit.app.presentation;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import app.trackfit.app.R;
import app.trackfit.domain.controller.DomainError;
import app.trackfit.domain.controller.Observation;
import app.trackfit.domain.controller.Outcome;
import app.trackfit.domain.controller.ProgramController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.engine.ExerciseSpecs;
import app.trackfit.domain.engine.ProgramDraft;
import app.trackfit.domain.engine.ProgramValidator;
import app.trackfit.domain.engine.TrainingEngine;
import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.CyclePhase;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.DaySlot;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.Session;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkState;
import app.trackfit.domain.ports.TimeSource;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Inject;

@HiltViewModel
public final class TrainingViewModel extends ViewModel {
    public enum Mode { NO_PROGRAM, REST, WAITING, READY, RUNNING, CLOSED }

    public record ProgramCard(String id, String name, boolean editEnabled, boolean startEnabled) {}
    public record CalendarCard(String date, String day, String marker) {}
    public record HomeState(String program, List<CalendarCard> calendar, Mode mode, DayCard day,
                            String status, int checked, Instant deadline) {}
    public record ExerciseCard(String id, String name, String type, String parameters, String note, boolean checked) {}
    public record DayCard(String name, boolean work, int minutes, LocalTime autoStart, List<ExerciseCard> exercises) {}
    public record DraftState(String name, List<DayCard> days) {}
    public record ReportState(UUID id, boolean cycle, String message) {}
    public enum InputKind { TEXT, INTEGER, DECIMAL, DURATION, SIDE }
    public record FieldState(String key, int label, InputKind kind, boolean required, String value) {}
    public record ExerciseType(String key, int label) {}

    private final Application app;
    private final ProgramController programs;
    private final TrainingController training;
    private final TimeSource time;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private final Observation.Subscription trainingSubscription;
    private final Observation.Subscription programsSubscription;
    private TrainingState state = TrainingState.empty();
    private List<ProgramController.ProgramSummary> catalog = new ArrayList<>();
    private ProgramDraft draft;
    private ProgramDraft beforeDayEdit;
    private boolean cleared;
    private boolean loaded;
    private int revision;

    @Inject
    public TrainingViewModel(Application app, ProgramController programs, TrainingController training, TimeSource time) {
        this.app = app;
        this.programs = programs;
        this.training = training;
        this.time = time;
        trainingSubscription = training.state().subscribe(value -> main.post(() -> {
            if (cleared) return;
            state = value;
            invalidate();
        }));
        programsSubscription = programs.programs().subscribe(value -> main.post(() -> {
            if (cleared) return;
            catalog = value;
            invalidate();
        }));
    }

    public LiveData<Integer> changes() { return changes; }

    public void refresh() {
        training.reconcile().whenComplete((result, failure) -> main.post(() -> {
            if (!cleared && !loaded && failure == null && result.succeeded()) {
                loaded = true;
                invalidate();
            }
        }));
        programs.refresh();
    }

    public boolean loaded() {
        return loaded;
    }

    public List<ProgramCard> catalog() {
        List<ProgramCard> result = new ArrayList<>();
        for (var summary : catalog) {
            boolean active = state.run() != null && state.run().program().id().equals(summary.id());
            result.add(new ProgramCard(summary.id().value().toString(), summary.name(), !active, state.run() == null));
        }
        return result;
    }

    public HomeState home() {
        var run = state.run();
        if (run == null) return new HomeState("", java.util.Collections.emptyList(), Mode.NO_PROGRAM, null, "", 0, null);
        LocalDate today = time.now().atZone(time.zone()).toLocalDate();
        DaySlot current = null;
        List<CalendarCard> calendar = new ArrayList<>();
        for (DaySlot slot : run.phase().slots()) {
            CycleDay day = run.program().days().get(slot.dayIndex());
            String marker = slot.date().equals(today) ? text(R.string.cycle_today)
                    : slot.date().isAfter(today) ? text(R.string.cycle_locked)
                    : slot.work() == null ? text(R.string.day_rest) : text(statusLabel(slot.work().status()));
            calendar.add(new CalendarCard(slot.date().format(DateTimeFormatter.ofPattern("dd.MM")), name(day), marker));
            if (today.equals(slot.date())) current = slot;
        }
        if (current == null) {
            return new HomeState(run.program().name(), calendar, Mode.WAITING, null, "", 0, null);
        }
        CycleDay day = run.program().days().get(current.dayIndex());
        WorkState work = current.work();
        if (work == null) return new HomeState(run.program().name(), calendar, Mode.REST, day(day, null), "", 0, null);
        Session session = null;
        Mode mode = Mode.READY;
        if (work instanceof WorkState.Running running) {
            mode = Mode.RUNNING;
            session = running.session();
        } else if (work instanceof WorkState.Closed closed) {
            mode = Mode.CLOSED;
            session = closed.session();
        }
        return new HomeState(run.program().name(), calendar, mode, day(day, session),
                text(statusLabel(work.status())), session == null ? 0 : session.checked().size(),
                session == null ? null : session.deadlineAt());
    }

    public ReportState nextReport() {
        for (Report report : state.reports()) {
            if (report.viewed()) continue;
            if (report instanceof Report.Day day) {
                WorkState.Status status = WorkState.Status.valueOf(day.result().name());
                return new ReportState(day.id(), false, text(R.string.report_day, day.dayName(),
                        text(statusLabel(status)), day.checked(), day.total()));
            }
            if (report instanceof Report.Cycle cycle) {
                int outcome = switch (cycle.outcome()) {
                    case FULL_SUCCESS -> R.string.cycle_success;
                    case PARTIAL_SUCCESS -> R.string.cycle_partial;
                    case FULL_FAILURE -> R.string.cycle_failure;
                };
                return new ReportState(cycle.id(), true, text(R.string.report_cycle, cycle.cycleNumber(), text(outcome)));
            }
        }
        return null;
    }

    public String countdown(Instant deadline) {
        long seconds = Math.max(0, Duration.between(time.now(), deadline).getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    public void startDays(String id, Consumer<List<String>> ready, Consumer<String> error) {
        complete(programs.load(programId(id)), error, program -> {
            List<String> labels = new ArrayList<>();
            List<CycleDay> days = program.days();
            for (int i = 0; i < days.size(); i++) labels.add(text(R.string.start_day_label, i + 1, name(days.get(i))));
            ready.accept(labels);
        });
    }

    public void startProgram(String id, int index, Runnable success, Consumer<String> error) {
        complete(training.startProgram(programId(id), index), error, ignored -> success.run());
    }

    public void startWorkout(Runnable success, Consumer<String> error) {
        complete(training.startWorkout(), error, ignored -> success.run());
    }

    public void finishWorkout(Runnable success, Consumer<String> error) {
        complete(training.finishWorkout(), error, ignored -> success.run());
    }

    public void stopProgram(Consumer<String> error) { complete(training.stopProgram(), error, ignored -> {}); }

    public void checkExercise(String id, Consumer<String> error) {
        complete(training.checkExercise(new ExerciseId(UUID.fromString(id))), error, ignored -> {});
    }

    public void dismissReport(UUID id, Consumer<String> error) {
        complete(training.markReportViewed(id), error, ignored -> {});
    }

    public void decideCycle(boolean newCycle, Runnable success, Consumer<String> error) {
        var decision = newCycle ? TrainingEngine.CycleDecision.CONTINUE : TrainingEngine.CycleDecision.SWITCH_PROGRAM;
        complete(training.decideCycle(decision), error, ignored -> success.run());
    }

    public void newProgram() {
        draft = programs.createDraft();
        beforeDayEdit = null;
    }

    public void editProgram(String id, Runnable ready, Consumer<String> error) {
        complete(programs.load(programId(id)), error, program -> {
            draft = new ProgramDraft(program);
            beforeDayEdit = null;
            ready.run();
        });
    }

    public void saveProgram(Runnable success, Consumer<String> error) {
        if (draft == null) return;
        complete(programs.save(draft), error, ignored -> {
            discardProgram();
            success.run();
        });
    }

    public void discardProgram() {
        draft = null;
        beforeDayEdit = null;
    }

    public DraftState draft() {
        if (draft == null) return null;
        List<DayCard> days = new ArrayList<>();
        Program snapshot = draft.snapshot();
        for (CycleDay entry : snapshot.days()) days.add(day(entry, null));
        return new DraftState(snapshot.name(), days);
    }

    public void renameProgram(String name) { draft = draft.rename(name); }
    public void addRest() { draft = draft.addRest(new DayId(UUID.randomUUID())); }
    public void removeDay(int index) { draft = draft.removeDay(index); }
    public void moveDay(int from, int to) { draft = draft.moveDay(from, to); }

    public int addWork() {
        beforeDayEdit = draft;
        int index = draft.snapshot().days().size();
        draft = draft.addWork(new DayId(UUID.randomUUID()), number -> text(R.string.workout_number, number));
        return index;
    }

    public void editDay() { beforeDayEdit = draft; }

    public void saveDay() { beforeDayEdit = null; }

    public void discardDay() {
        if (beforeDayEdit != null) draft = beforeDayEdit;
        beforeDayEdit = null;
    }

    public void updateDay(int index, String name, String minutes, LocalTime autoStart) {
        Integer duration = integer(minutes);
        draft = draft.editWork(index, name, duration == null ? 0 : duration, autoStart);
    }

    public void removeExercise(int day, int index) { draft = draft.removeExercise(day, index); }

    public List<ExerciseType> exerciseTypes() {
        List<ExerciseType> result = new ArrayList<>();
        for (Exercise.Type type : Exercise.Type.values()) {
            result.add(new ExerciseType(type.name(), typeLabel(type)));
        }
        return result;
    }

    public List<FieldState> exerciseFields(String typeKey, int day, int index) {
        Exercise.Type type = Exercise.Type.valueOf(typeKey);
        Exercise exercise = index < 0 ? null : exercise(day, index);
        var spec = ExerciseSpecs.forType(type);
        List<FieldState> fields = new ArrayList<>();
        for (var field : spec.required()) fields.add(field(field, true, exercise));
        for (var field : spec.optional()) fields.add(field(field, false, exercise));
        return fields;
    }

    public String exerciseTypeKey(int day, int index) { return exercise(day, index).type().name(); }

    public boolean saveExercise(String typeKey, int day, int index, Map<String, String> inputs) {
        Map<ExerciseSpecs.Field, String> values = new EnumMap<>(ExerciseSpecs.Field.class);
        inputs.forEach((key, value) -> values.put(ExerciseSpecs.Field.valueOf(key), value));
        Exercise.Type type = Exercise.Type.valueOf(typeKey);
        ExerciseId id = index < 0 ? new ExerciseId(UUID.randomUUID()) : exercise(day, index).id();
        String name = values.getOrDefault(ExerciseSpecs.Field.NAME, "");
        String note = optional(values.get(ExerciseSpecs.Field.NOTE));
        Integer sets = integer(values.get(ExerciseSpecs.Field.SETS));
        Integer reps = integer(values.get(ExerciseSpecs.Field.REPS));
        Integer rest = integer(values.get(ExerciseSpecs.Field.REST_SECONDS));
        Integer pulse = integer(values.get(ExerciseSpecs.Field.TARGET_PULSE));
        int duration = seconds(values.get(ExerciseSpecs.Field.DURATION_SECONDS));
        Exercise.BodySide side = side(values.get(ExerciseSpecs.Field.SIDE));
        String weightText = optional(values.get(ExerciseSpecs.Field.WEIGHT_KG));
        Double weight;
        try {
            weight = weightText == null ? null : Double.valueOf(weightText.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (invalidInteger(values, ExerciseSpecs.Field.SETS, sets)
                || invalidInteger(values, ExerciseSpecs.Field.REPS, reps)
                || invalidInteger(values, ExerciseSpecs.Field.REST_SECONDS, rest)
                || invalidInteger(values, ExerciseSpecs.Field.TARGET_PULSE, pulse)) return false;
        Exercise replacement = switch (type) {
            case STRENGTH -> new Exercise.Strength(id, name, note, sets == null ? 0 : sets,
                    reps == null ? 0 : reps, weight, rest);
            case CARDIO -> new Exercise.Cardio(id, name, note, duration,
                    optional(values.get(ExerciseSpecs.Field.INTENSITY)), pulse);
            case BALANCE -> new Exercise.Balance(id, name, note, duration, sets, side);
            case STRETCH -> new Exercise.Stretch(id, name, note, duration, sets, side);
        };
        if (!ProgramValidator.invalidFields(replacement).isEmpty()) return false;
        draft = index < 0 ? draft.addExercise(day, replacement) : draft.editExercise(day, index, replacement);
        return true;
    }

    private Exercise exercise(int day, int index) {
        return ((CycleDay.Work) draft.snapshot().days().get(day)).exercises().get(index);
    }

    private FieldState field(ExerciseSpecs.Field field, boolean required, Exercise exercise) {
        var kind = switch (field) {
            case NAME, NOTE, INTENSITY -> InputKind.TEXT;
            case WEIGHT_KG -> InputKind.DECIMAL;
            case DURATION_SECONDS -> InputKind.DURATION;
            case SIDE -> InputKind.SIDE;
            default -> InputKind.INTEGER;
        };
        Object value = exercise == null ? null : ExerciseSpecs.value(exercise, field);
        String display = value == null ? "" : value.toString();
        if (kind == InputKind.DURATION && value != null) {
            int s = (int) value;
            display = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        }
        return new FieldState(field.name(), fieldLabel(field), kind, required, display);
    }

    private DayCard day(CycleDay day, Session session) {
        if (!(day instanceof CycleDay.Work work)) {
            return new DayCard(text(R.string.day_rest), false, 0, null, java.util.Collections.emptyList());
        }
        List<ExerciseCard> exercises = new ArrayList<>();
        for (Exercise exercise : work.exercises()) {
            var parameters = new ArrayList<String>();
            List<ExerciseSpecs.Field> fields = session == null
                    ? ExerciseSpecs.forType(exercise.type()).summary()
                    : workoutFields(exercise.type());
            for (var field : fields) {
                if (field == ExerciseSpecs.Field.NAME || field == ExerciseSpecs.Field.NOTE) continue;
                FieldState value = field(field, false, exercise);
                if (value.value().isEmpty()) continue;
                String label = value.kind() == InputKind.SIDE ? text(sideLabel(side(value.value()))) : value.value();
                parameters.add(text(R.string.field_value, text(value.label()), label));
            }
            exercises.add(new ExerciseCard(exercise.id().value().toString(), exercise.name(),
                    text(typeTag(exercise.type())), String.join(" · ", parameters), exercise.note(),
                    session != null && session.checked().containsKey(exercise.id())));
        }
        return new DayCard(work.name(), true, work.durationMinutes(), work.autoStart(), exercises);
    }

    private static List<ExerciseSpecs.Field> workoutFields(Exercise.Type type) {
        var spec = ExerciseSpecs.forType(type);
        var fields = new ArrayList<>(spec.required());
        fields.addAll(spec.optional());
        return fields;
    }

    private static int seconds(String duration) {
        if (duration == null) return 0;
        String[] parts = duration.trim().split(":", -1);
        if (parts.length != 2) return 0;
        Integer minutes = integer(parts[0]);
        Integer seconds = integer(parts[1]);
        if (minutes == null || seconds == null || minutes < 0 || seconds < 0 || seconds >= 60) return 0;
        long total = minutes * 60L + seconds;
        return total > Integer.MAX_VALUE ? 0 : (int) total;
    }

    private static boolean invalidInteger(Map<ExerciseSpecs.Field, String> values, ExerciseSpecs.Field field,
                                          Integer value) {
        return optional(values.get(field)) != null && value == null;
    }

    private static Integer integer(String value) {
        try {
            return optional(value) == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String optional(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    private static Exercise.BodySide side(String value) {
        return optional(value) == null ? null : Exercise.BodySide.valueOf(value);
    }

    private static int sideLabel(Exercise.BodySide side) {
        return switch (side) {
            case LEFT -> R.string.field_side_left;
            case RIGHT -> R.string.field_side_right;
            case BOTH -> R.string.field_side_both;
        };
    }

    private static int statusLabel(WorkState.Status status) {
        return switch (status) {
            case NOT_STARTED -> R.string.status_pending;
            case RUNNING -> R.string.status_running;
            case COMPLETED -> R.string.status_completed;
            case INCOMPLETE -> R.string.status_incomplete;
            case SKIPPED -> R.string.status_skipped;
        };
    }

    private static int fieldLabel(ExerciseSpecs.Field field) {
        return switch (field) {
            case NAME -> R.string.exercise_name;
            case SETS -> R.string.exercise_sets;
            case REPS -> R.string.exercise_reps;
            case WEIGHT_KG -> R.string.exercise_weight;
            case REST_SECONDS -> R.string.exercise_rest;
            case DURATION_SECONDS -> R.string.exercise_duration;
            case INTENSITY -> R.string.exercise_intensity;
            case TARGET_PULSE -> R.string.exercise_pulse;
            case SIDE -> R.string.exercise_side;
            case NOTE -> R.string.exercise_note;
        };
    }

    private static int typeLabel(Exercise.Type type) {
        return switch (type) {
            case STRENGTH -> R.string.exercise_strength;
            case CARDIO -> R.string.exercise_cardio;
            case BALANCE -> R.string.exercise_balance;
            case STRETCH -> R.string.exercise_stretch;
        };
    }

    private static int typeTag(Exercise.Type type) {
        return switch (type) {
            case STRENGTH -> R.string.exercise_type_strength;
            case CARDIO -> R.string.exercise_type_cardio;
            case BALANCE -> R.string.exercise_type_balance;
            case STRETCH -> R.string.exercise_type_stretch;
        };
    }

    private String name(CycleDay day) {
        return day instanceof CycleDay.Work work ? work.name() : text(R.string.day_rest);
    }

    private static ProgramId programId(String id) { return new ProgramId(UUID.fromString(id)); }

    private String text(int id, Object... args) { return app.getString(id, args); }

    private void invalidate() { changes.setValue(++revision); }

    private <T> void complete(CompletableFuture<Outcome<T>> future, Consumer<String> error, Consumer<T> success) {
        future.whenComplete((result, failure) -> main.post(() -> {
            if (cleared) return;
            if (failure != null) {
                error.accept(text(R.string.error_storage));
            } else if (result.succeeded()) {
                success.accept(result.value());
            } else {
                error.accept(errorMessage(result.error()));
            }
        }));
    }

    private String errorMessage(DomainError error) {
        if (error instanceof DomainError.ValidationFailed failure) {
            var issue = failure.issues().get(0);
            int message = switch (issue.code()) {
                case PROGRAM_NAME -> R.string.error_program_name;
                case EMPTY_CYCLE, NO_WORK_DAY -> R.string.error_empty_cycle;
                case DAY_NAME -> R.string.error_day_name;
                case DAY_DURATION -> R.string.error_day_duration;
                case EMPTY_WORK_DAY -> R.string.error_empty_workout;
                default -> R.string.error_exercise;
            };
            return text(message);
        }
        if (error == DomainError.CommandFailure.PROGRAM_NOT_EDITABLE) return text(R.string.error_active_edit);
        if (error instanceof DomainError.StorageFailure) return text(R.string.error_storage);
        return text(R.string.error_action);
    }

    @Override
    protected void onCleared() {
        cleared = true;
        trainingSubscription.close();
        programsSubscription.close();
        main.removeCallbacksAndMessages(null);
    }
}
