package app.trackfit.infra.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Rows.ProgramRow.class, Rows.DayRow.class, Rows.ExerciseRow.class,
        Rows.TrainingRow.class, Rows.ReportRow.class, Rows.LogRow.class, Rows.CoachMemoryRow.class}, version = 2, exportSchema = true)
public abstract class TrackFitDatabase extends RoomDatabase implements AutoCloseable {
    public static final Migration MIGRATE_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `coach_memory` (`id` INTEGER NOT NULL, "
                    + "`last_line_at` TEXT, `motivated_session` TEXT, PRIMARY KEY(`id`))");
        }
    };

    public abstract TrackFitDao dao();
}
