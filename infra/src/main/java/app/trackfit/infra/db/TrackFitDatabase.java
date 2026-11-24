package app.trackfit.infra.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Rows.ProgramRow.class, Rows.DayRow.class, Rows.ExerciseRow.class,
        Rows.TrainingRow.class, Rows.ReportRow.class, Rows.LogRow.class, Rows.CoachMemoryRow.class, Rows.ProfileRow.class, Rows.RecordRow.class,
        Rows.ParameterRow.class}, version = 3, exportSchema = true)
public abstract class TrackFitDatabase extends RoomDatabase implements AutoCloseable {
    public static final Migration MIGRATE_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `coach_memory` (`id` INTEGER NOT NULL, "
                    + "`last_line_at` TEXT, `motivated_session` TEXT, PRIMARY KEY(`id`))");
        }
    };

    public static final Migration MIGRATE_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, "
                    + "`sex` TEXT NOT NULL, `birthday` TEXT, `avatar` TEXT, PRIMARY KEY(`id`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `personal_record` (`id` TEXT NOT NULL, `exercise` TEXT NOT NULL, "
                    + "`date` TEXT NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `record_param` (`record_id` TEXT NOT NULL, `position` INTEGER NOT NULL, "
                    + "`name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`record_id`, `position`), "
                    + "FOREIGN KEY(`record_id`) REFERENCES `personal_record`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        }
    };

    public abstract TrackFitDao dao();
}
