package app.trackfit.infra.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Rows.ProgramRow.class, Rows.DayRow.class, Rows.ExerciseRow.class,
        Rows.TrainingRow.class, Rows.ReportRow.class, Rows.LogRow.class}, version = 1, exportSchema = true)
public abstract class TrackFitDatabase extends RoomDatabase implements AutoCloseable {
    public abstract TrackFitDao dao();
}
