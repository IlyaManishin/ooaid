package app.trackfit.infra.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

public final class Rows {
    private Rows() {}

    @Entity(tableName = "program")
    public static class ProgramRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String name = "";
    }

    @Entity(tableName = "program_day",
            foreignKeys = @ForeignKey(entity = ProgramRow.class, parentColumns = "id",
                    childColumns = "program_id", onDelete = ForeignKey.CASCADE),
            indices = @Index(value = {"program_id", "position"}))
    public static class DayRow {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "program_id") @NonNull public String programId = "";
        public int position;
        @NonNull public String kind = "";
        public String name;
        @ColumnInfo(name = "duration_min") public Integer durationMinutes;
        @ColumnInfo(name = "autostart_min_of_day") public Integer autoStartMinute;
    }

    @Entity(tableName = "exercise",
            foreignKeys = @ForeignKey(entity = DayRow.class, parentColumns = "id",
                    childColumns = "day_id", onDelete = ForeignKey.CASCADE),
            indices = @Index(value = {"day_id", "position"}))
    public static class ExerciseRow {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "day_id") @NonNull public String dayId = "";
        public int position;
        @NonNull public String type = "";
        @NonNull public String name = "";
        public String note;
        public Integer sets;
        public Integer reps;
        @ColumnInfo(name = "weight_kg") public Double weight;
        @ColumnInfo(name = "rest_seconds") public Integer rest;
        @ColumnInfo(name = "duration_seconds") public Integer duration;
        public String intensity;
        @ColumnInfo(name = "target_pulse") public Integer pulse;
        public String side;
    }

    @Entity(tableName = "training_state")
    public static class TrainingRow {
        @PrimaryKey public int id = 1;
        @ColumnInfo(name = "run_json") public String runJson;
        @ColumnInfo(name = "schema_version") public int schemaVersion;
    }

    @Entity(tableName = "report")
    public static class ReportRow {
        @PrimaryKey @NonNull public String id = "";
        @ColumnInfo(name = "created_at") public long createdAt;
        public int position;
        public boolean viewed;
        @NonNull public String kind = "";
        @NonNull public String details = "";
    }

    @Entity(tableName = "workout_log", indices = @Index(value = {"date", "result"}))
    public static class LogRow {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String date = "";
        @NonNull public String result = "";
        @NonNull public String reason = "";
        @ColumnInfo(name = "program_name") @NonNull public String programName = "";
        @ColumnInfo(name = "day_name") @NonNull public String dayName = "";
        @ColumnInfo(name = "started_at") public Long startedAt;
        @ColumnInfo(name = "closed_at") public long closedAt;
        @ColumnInfo(name = "cycle_number") public int cycleNumber;
        @ColumnInfo(name = "exercises_json") @NonNull public String exercisesJson = "";
    }
}
