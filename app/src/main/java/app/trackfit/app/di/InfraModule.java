package app.trackfit.app.di;

import android.content.Context;
import android.content.SharedPreferences;
import app.trackfit.domain.ports.CoachMemoryStore;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.ProfileRepository;
import app.trackfit.domain.ports.RecordsRepository;
import app.trackfit.domain.ports.SettingsRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.domain.ports.TrainingStateStore;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.domain.ports.WorkoutLogRepository;
import app.trackfit.infra.db.RoomTrackFitStore;
import app.trackfit.infra.prefs.LocalSettings;
import app.trackfit.infra.time.SystemTimeSource;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public final class InfraModule {
    private InfraModule() {}

    @Provides @Singleton
    static RoomTrackFitStore provideStore(@ApplicationContext Context context) {
        return RoomTrackFitStore.open(context);
    }

    @Provides static CoachMemoryStore provideCoachMemory(RoomTrackFitStore store) { return store; }
    @Provides static ProfileRepository provideProfile(RoomTrackFitStore store) { return store; }
    @Provides static RecordsRepository provideRecords(RoomTrackFitStore store) { return store; }
    @Provides static ProgramRepository providePrograms(RoomTrackFitStore store) { return store; }
    @Provides static TrainingStateStore provideTraining(RoomTrackFitStore store) { return store; }
    @Provides static TransactionRunner provideTransactions(RoomTrackFitStore store) { return store; }
    @Provides static WorkoutLogRepository provideLog(RoomTrackFitStore store) { return store; }
    @Provides @Singleton static TimeSource provideTime() { return new SystemTimeSource(); }

    @Provides @Singleton
    static SettingsRepository provideSettings(@ApplicationContext Context context) {
        SharedPreferences preferences = context.getSharedPreferences("trackfit_settings", Context.MODE_PRIVATE);
        return new LocalSettings(preferences);
    }
}
