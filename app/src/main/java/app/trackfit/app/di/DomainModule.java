package app.trackfit.app.di;

import android.content.Context;
import app.trackfit.app.R;
import app.trackfit.domain.controller.CoachController;
import app.trackfit.domain.controller.DomainEvents;
import app.trackfit.domain.ports.CoachMemoryStore;
import app.trackfit.domain.ports.SettingsRepository;
import app.trackfit.infra.assets.CoachAssets;
import app.trackfit.domain.controller.ProgramController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.domain.ports.TrainingStateStore;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.domain.ports.WorkoutLogRepository;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public final class DomainModule {
    private DomainModule() {}

    @Provides @Singleton
    static Executor provideWriter() {
        return Executors.newSingleThreadExecutor();
    }

    @Provides @Singleton
    static DomainEvents provideEvents() {
        return new DomainEvents();
    }

    @Provides @Singleton
    static CoachController provideCoach(@ApplicationContext Context context, CoachMemoryStore store,
                                       SettingsRepository settings, TimeSource time, Executor writer) {
        return new CoachController(() -> CoachAssets.load(context.getAssets(), context.getString(R.string.coach_fallback)),
                store, settings, time, writer);
    }

    @Provides @Singleton
    static TrainingController provideTraining(TrainingStateStore store, WorkoutLogRepository log,
            ProgramRepository programs, TransactionRunner transactions, TimeSource time, Executor writer,
            DomainEvents events) {
        return new TrainingController(store, log, programs, transactions, time, writer, events);
    }

    @Provides @Singleton
    static ProgramController providePrograms(ProgramRepository repository, TransactionRunner transactions,
                                               TrainingController training) {
        return new ProgramController(repository, transactions, training);
    }
}
