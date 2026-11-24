package app.trackfit.infra.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.List;

@Dao
public interface TrackFitDao {
    @Query("SELECT * FROM program ORDER BY name COLLATE NOCASE, id")
    List<Rows.ProgramRow> programs();

    @Query("SELECT * FROM program WHERE id = :id")
    Rows.ProgramRow program(String id);

    @Query("SELECT * FROM program_day WHERE program_id = :id ORDER BY position")
    List<Rows.DayRow> days(String id);

    @Query("SELECT * FROM exercise WHERE day_id = :id ORDER BY position")
    List<Rows.ExerciseRow> exercises(String id);

    @Upsert void saveProgram(Rows.ProgramRow program);

    @Query("DELETE FROM program_day WHERE program_id = :id")
    void clearDays(String id);

    @Insert void insertDay(Rows.DayRow day);
    @Insert void insertExercises(List<Rows.ExerciseRow> exercises);

    @Query("SELECT * FROM training_state WHERE id = 1")
    Rows.TrainingRow training();

    @Upsert void saveTraining(Rows.TrainingRow state);

    @Query("SELECT * FROM report ORDER BY position")
    List<Rows.ReportRow> reports();

    @Query("DELETE FROM report")
    void clearReports();

    @Insert void insertReports(List<Rows.ReportRow> reports);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertLog(List<Rows.LogRow> log);

    @Query("SELECT * FROM workout_log ORDER BY closed_at, id")
    List<Rows.LogRow> log();

    @Query("SELECT COUNT(*) FROM workout_log WHERE date = :date AND result = 'COMPLETED'")
    int fullCompletionsOn(String date);
}
