package app.trackfit.app.di;

import app.trackfit.domain.controller.DomainEvents;
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
