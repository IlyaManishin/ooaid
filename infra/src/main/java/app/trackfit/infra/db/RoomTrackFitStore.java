package app.trackfit.infra.db;

import android.content.Context;
import androidx.room.Room;
import app.trackfit.domain.coach.CoachMemory;
import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.ports.CoachMemoryStore;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.model.Report;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.model.WorkoutLogEntry;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TrainingStateStore;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.domain.ports.WorkoutLogRepository;
import app.trackfit.infra.mapping.ProgramRows;
import app.trackfit.infra.mapping.TrainingJson;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class RoomTrackFitStore implements ProgramRepository, TrainingStateStore, WorkoutLogRepository,
        TransactionRunner, CoachMemoryStore {
    private final TrackFitDatabase database;
    private final TrackFitDao dao;

    public RoomTrackFitStore(TrackFitDatabase database) {
        this.database = database;
        this.dao = database.dao();
    }

    public static RoomTrackFitStore open(Context context) {
        return new RoomTrackFitStore(Room.databaseBuilder(context, TrackFitDatabase.class, "trackfit.db")
                .addMigrations(TrackFitDatabase.MIGRATE_1_2).build());
    }

    @Override
    public <T> T inTransaction(Supplier<T> transaction) {
        return database.runInTransaction(transaction::get);
    }

    @Override
    public List<Program> programs() {
        return inTransaction(() -> {
            List<Program> programs = new ArrayList<>();
            for (Rows.ProgramRow row : dao.programs()) {
                programs.add(readProgram(row));
            }
            return programs;
        });
    }

    @Override
    public Optional<Program> load(ProgramId id) {
        return inTransaction(() -> Optional.ofNullable(dao.program(id.value().toString())).map(this::readProgram));
    }

    private Program readProgram(Rows.ProgramRow row) {
        return ProgramRows.read(row, dao.days(row.id), dao::exercises);
    }

    @Override
    public void save(Program program) {
        database.runInTransaction(() -> {
            Rows.ProgramRow row = ProgramRows.toRow(program);
            dao.saveProgram(row);
            dao.clearDays(row.id);
            for (int dayIndex = 0; dayIndex < program.days().size(); dayIndex++) {
                CycleDay day = program.days().get(dayIndex);
                Rows.DayRow dayRow = ProgramRows.toRow(day, row.id, dayIndex);
                dao.insertDay(dayRow);
                if (day instanceof CycleDay.Work work) {
                    List<Rows.ExerciseRow> exercises = new ArrayList<>();
                    for (int index = 0; index < work.exercises().size(); index++) {
                        exercises.add(ProgramRows.toRow(work.exercises().get(index), dayRow.id, index));
                    }
                    dao.insertExercises(exercises);
                }
            }
        });
    }

    @Override
    public TrainingState loadTraining() {
        return inTransaction(() -> {
            Rows.TrainingRow row = dao.training();
            List<Report> reports = new ArrayList<>();
            for (Rows.ReportRow report : dao.reports()) reports.add(TrainingJson.readReport(report));
            return new TrainingState(row == null ? null : TrainingJson.readRun(row.runJson, row.schemaVersion), reports);
        });
    }

    @Override
    public void commit(TrainingState state, List<WorkoutLogEntry> logAppends) {
        database.runInTransaction(() -> {
            Rows.TrainingRow row = new Rows.TrainingRow();
            row.schemaVersion = TrainingJson.SCHEMA_VERSION;
            row.runJson = TrainingJson.writeRun(state.run());
            dao.saveTraining(row);
            List<Rows.ReportRow> reports = new ArrayList<>();
            for (int i = 0; i < state.reports().size(); i++) {
                reports.add(TrainingJson.writeReport(state.reports().get(i), i));
            }
            dao.clearReports();
            dao.insertReports(reports);
            if (!logAppends.isEmpty()) {
                List<Rows.LogRow> log = new ArrayList<>();
                for (WorkoutLogEntry entry : logAppends) log.add(TrainingJson.writeLog(entry));
                dao.insertLog(log);
            }
        });
    }

    @Override
    public CoachMemory loadCoachMemory() {
        Rows.CoachMemoryRow row = dao.coachMemory();
        return row == null ? CoachMemory.empty()
                : new CoachMemory(row.lastLineAt == null ? null : Instant.parse(row.lastLineAt), row.motivatedSession);
    }

    @Override
    public void saveCoachMemory(CoachMemory memory) {
        Rows.CoachMemoryRow row = new Rows.CoachMemoryRow();
        row.lastLineAt = memory.lastLineAt() == null ? null : memory.lastLineAt().toString();
        row.motivatedSession = memory.motivatedSession();
        dao.saveCoachMemory(row);
    }

    @Override
    public List<WorkoutLogEntry> entries() {
        List<WorkoutLogEntry> entries = new ArrayList<>();
        for (Rows.LogRow row : dao.log()) entries.add(TrainingJson.readLog(row));
        return entries;
    }

    @Override
    public int fullCompletionsOn(LocalDate date) {
        return dao.fullCompletionsOn(date.toString());
    }
}
