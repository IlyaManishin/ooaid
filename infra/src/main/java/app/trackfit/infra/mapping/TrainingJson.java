package app.trackfit.infra.mapping;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.CycleOutcome;
import app.trackfit.domain.model.CyclePhase;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.DaySlot;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.model.ProgramRun;
import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.Session;
import app.trackfit.domain.model.WorkState;
import app.trackfit.domain.model.WorkoutLogEntry;
import app.trackfit.infra.db.Rows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TrainingJson {
    public static final int SCHEMA_VERSION = 1;

    private TrainingJson() {}

    public static String writeRun(ProgramRun run) {
        if (run == null) return null;
        return json(() -> {
            JSONObject object = new JSONObject()
                    .put("id", run.id().toString())
                    .put("program", writeProgram(run.program()))
                    .put("start_index", run.startDayIndex())
                    .put("cycle", run.cycleNumber())
                    .put("reconciled", run.lastReconciledAt().toString())
                    .put("slots", writeList(run.phase().slots(), TrainingJson::writeSlot));
            if (run.phase() instanceof CyclePhase.AwaitingDecision awaiting) {
                object.put("cycle_outcome", awaiting.outcome().name())
                        .put("cycle_report", awaiting.reportId().toString());
            }
            return object.toString();
        });
    }

    public static ProgramRun readRun(String text, int version) {
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unknown training state version " + version);
        }
        if (text == null) return null;
        return json(() -> {
            JSONObject object = new JSONObject(text);
            List<DaySlot> slots = readList(object.getJSONArray("slots"), TrainingJson::readSlot);
            CyclePhase phase = object.has("cycle_outcome")
                    ? new CyclePhase.AwaitingDecision(slots, CycleOutcome.valueOf(object.getString("cycle_outcome")),
                            uuid(object, "cycle_report"))
                    : new CyclePhase.InProgress(slots);
            return new ProgramRun(uuid(object, "id"), readProgram(object.getJSONObject("program")),
                    object.getInt("start_index"), object.getInt("cycle"), phase, instant(object, "reconciled"));
        });
    }

    private static JSONObject writeProgram(Program program) throws JSONException {
        return new JSONObject().put("id", program.id().value().toString()).put("name", program.name())
                .put("days", writeList(program.days(), day -> {
                    JSONObject object = new JSONObject().put("id", day.id().value().toString());
                    if (day instanceof CycleDay.Work work) {
                        object.put("name", work.name()).put("duration", work.durationMinutes())
                                .put("auto_start", work.autoStart() == null ? null : work.autoStart().toString())
                                .put("exercises", writeList(work.exercises(), TrainingJson::writeExercise));
                    }
                    return object;
                }));
    }

    private static Program readProgram(JSONObject object) throws JSONException {
        return new Program(new ProgramId(uuid(object, "id")), object.getString("name"),
                readList(object.getJSONArray("days"), day -> {
                    DayId id = new DayId(uuid(day, "id"));
                    if (!day.has("exercises")) return new CycleDay.Rest(id);
                    return new CycleDay.Work(id, day.getString("name"), day.getInt("duration"),
                            day.has("auto_start") ? LocalTime.parse(day.getString("auto_start")) : null,
                            readList(day.getJSONArray("exercises"), TrainingJson::readExercise));
                }));
    }

    private static JSONObject writeExercise(Exercise exercise) throws JSONException {
        Rows.ExerciseRow row = ProgramRows.toRow(exercise, "", 0);
        return new JSONObject().put("id", row.id).put("type", row.type).put("name", row.name)
                .put("note", row.note).put("sets", row.sets).put("reps", row.reps).put("weight", row.weight)
                .put("rest", row.rest).put("duration", row.duration).put("intensity", row.intensity)
                .put("pulse", row.pulse).put("side", row.side);
    }

    private static Exercise readExercise(JSONObject object) throws JSONException {
        Rows.ExerciseRow row = new Rows.ExerciseRow();
        row.id = object.getString("id");
        row.type = object.getString("type");
        row.name = object.getString("name");
        row.note = stringOrNull(object, "note");
        row.sets = integerOrNull(object, "sets");
        row.reps = integerOrNull(object, "reps");
        row.weight = object.isNull("weight") ? null : object.getDouble("weight");
        row.rest = integerOrNull(object, "rest");
        row.duration = integerOrNull(object, "duration");
        row.intensity = stringOrNull(object, "intensity");
        row.pulse = integerOrNull(object, "pulse");
        row.side = stringOrNull(object, "side");
        return ProgramRows.readExercise(row);
    }

    private static JSONObject writeSlot(DaySlot slot) throws JSONException {
        JSONObject object = new JSONObject().put("date", slot.date().toString()).put("day", slot.dayIndex());
        if (slot.work() == null) return object;
        object.put("status", slot.work().status().name()).put("auto", slot.work().autoStartEnabled());
        if (slot.work() instanceof WorkState.Running running) {
            object.put("session", writeSession(running.session()));
        } else if (slot.work() instanceof WorkState.Closed closed) {
            object.put("closed", closed.closedAt().toString()).put("reason", closed.reason().name());
            if (closed.session() != null) object.put("session", writeSession(closed.session()));
        }
        return object;
    }

    private static DaySlot readSlot(JSONObject object) throws JSONException {
        WorkState work = null;
        if (object.has("status")) {
            boolean auto = object.getBoolean("auto");
            Session session = object.has("session") ? readSession(object.getJSONObject("session")) : null;
            WorkState.Status status = WorkState.Status.valueOf(object.getString("status"));
            work = switch (status) {
                case NOT_STARTED -> new WorkState.NotStarted(auto);
                case RUNNING -> new WorkState.Running(auto, session);
                case COMPLETED, INCOMPLETE, SKIPPED -> new WorkState.Closed(auto,
                        WorkState.Result.valueOf(status.name()), session, instant(object, "closed"),
                        Session.CloseReason.valueOf(object.getString("reason")));
            };
        }
        return new DaySlot(LocalDate.parse(object.getString("date")), object.getInt("day"), work);
    }

    private static JSONObject writeSession(Session session) throws JSONException {
        JSONArray checked = new JSONArray();
        for (var check : session.checked().entrySet()) {
            checked.put(new JSONObject().put("exercise", check.getKey().value().toString())
                    .put("at", check.getValue().toString()));
        }
        return new JSONObject().put("start", session.startedAt().toString())
                .put("kind", session.startKind().name()).put("deadline", session.deadlineAt().toString())
                .put("timeout", session.timeoutReason().name()).put("checked", checked);
    }

    private static Session readSession(JSONObject object) throws JSONException {
        Map<ExerciseId, Instant> checks = new LinkedHashMap<>();
        JSONArray checked = object.getJSONArray("checked");
        for (int i = 0; i < checked.length(); i++) {
            JSONObject row = checked.getJSONObject(i);
            checks.put(new ExerciseId(uuid(row, "exercise")), instant(row, "at"));
        }
        return new Session(instant(object, "start"), Session.StartKind.valueOf(object.getString("kind")),
                instant(object, "deadline"), Session.CloseReason.valueOf(object.getString("timeout")), checks);
    }

    public static Rows.ReportRow writeReport(Report report, int position) {
        return json(() -> {
            Rows.ReportRow row = new Rows.ReportRow();
            row.id = report.id().toString();
            row.createdAt = report.createdAt().toEpochMilli();
            row.position = position;
            row.viewed = report.viewed();
            JSONObject details = new JSONObject();
            if (report instanceof Report.Day day) {
                row.kind = "DAY";
                details.put("date", day.date().toString()).put("name", day.dayName())
                        .put("result", day.result().name()).put("checked", day.checked()).put("total", day.total())
                        .put("earlier_success", day.earlierSuccess());
            } else if (report instanceof Report.Cycle cycle) {
                row.kind = "CYCLE";
                details.put("number", cycle.cycleNumber()).put("outcome", cycle.outcome().name());
            }
            row.details = details.toString();
            return row;
        });
    }

    public static Report readReport(Rows.ReportRow row) {
        return json(() -> {
            UUID id = UUID.fromString(row.id);
            Instant at = Instant.ofEpochMilli(row.createdAt);
            JSONObject details = new JSONObject(row.details);
            return switch (row.kind) {
                case "DAY" -> new Report.Day(id, at, row.viewed, LocalDate.parse(details.getString("date")),
                        details.getString("name"), WorkState.Result.valueOf(details.getString("result")),
                        details.getInt("checked"), details.getInt("total"), details.getBoolean("earlier_success"));
                case "CYCLE" -> new Report.Cycle(id, at, row.viewed, details.getInt("number"),
                        CycleOutcome.valueOf(details.getString("outcome")));
                default -> throw new IllegalArgumentException("Unknown report kind: " + row.kind);
            };
        });
    }

    public static Rows.LogRow writeLog(WorkoutLogEntry entry) {
        return json(() -> {
            Rows.LogRow row = new Rows.LogRow();
            row.id = entry.id().toString();
            row.date = entry.date().toString();
            row.programName = entry.programName();
            row.dayName = entry.dayName();
            row.result = entry.result().name();
            row.reason = entry.reason().name();
            row.startedAt = entry.startedAt() == null ? null : entry.startedAt().toEpochMilli();
            row.closedAt = entry.closedAt().toEpochMilli();
            row.cycleNumber = entry.cycleNumber();
            row.exercisesJson = writeList(entry.exercises(), exercise -> new JSONObject()
                    .put("name", exercise.name()).put("type", exercise.type().name())
                    .put("checked", exercise.checked())).toString();
            return row;
        });
    }

    public static WorkoutLogEntry readLog(Rows.LogRow row) {
        return json(() -> new WorkoutLogEntry(UUID.fromString(row.id), LocalDate.parse(row.date),
                row.programName, row.dayName, WorkState.Result.valueOf(row.result),
                Session.CloseReason.valueOf(row.reason), row.startedAt == null ? null : Instant.ofEpochMilli(row.startedAt),
                Instant.ofEpochMilli(row.closedAt), row.cycleNumber, readList(new JSONArray(row.exercisesJson),
                object -> new WorkoutLogEntry.ExerciseResult(object.getString("name"),
                        Exercise.Type.valueOf(object.getString("type")), object.getBoolean("checked")))));
    }

    private static UUID uuid(JSONObject object, String key) throws JSONException {
        return UUID.fromString(object.getString(key));
    }

    private static Instant instant(JSONObject object, String key) throws JSONException {
        return Instant.parse(object.getString(key));
    }

    private static String stringOrNull(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getString(key);
    }

    private static Integer integerOrNull(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getInt(key);
    }

    private static <T> JSONArray writeList(List<T> list, JsonWriter<T> writer) throws JSONException {
        JSONArray array = new JSONArray();
        for (T item : list) array.put(writer.write(item));
        return array;
    }

    private static <T> List<T> readList(JSONArray array, JsonReader<T> reader) throws JSONException {
        List<T> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) list.add(reader.read(array.getJSONObject(i)));
        return list;
    }

    private static <T> T json(JsonOperation<T> operation) {
        try {
            return operation.run();
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid TrackFit JSON", error);
        }
    }

    private interface JsonOperation<T> { T run() throws JSONException; }
    private interface JsonWriter<T> { JSONObject write(T value) throws JSONException; }
    private interface JsonReader<T> { T read(JSONObject json) throws JSONException; }
}
