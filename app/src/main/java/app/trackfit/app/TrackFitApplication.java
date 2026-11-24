package app.trackfit.app;

import android.app.Application;
import app.trackfit.domain.controller.CoachController;
import app.trackfit.domain.controller.DomainEvents;
import app.trackfit.domain.controller.ProgramController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.app.triggers.TrainingTriggers;
import dagger.hilt.android.HiltAndroidApp;
import java.util.concurrent.Executor;
import javax.inject.Inject;

@HiltAndroidApp
public final class TrackFitApplication extends Application {
    @Inject Executor writer;
    @Inject ProgramRepository programs;
    @Inject TransactionRunner transactions;
    @Inject ProgramController programController;
    @Inject TrainingTriggers triggers;
    @Inject TrainingController training;
    @Inject CoachController coach;
    @Inject DomainEvents events;

    @Override
    public void onCreate() {
        super.onCreate();
        StartupSeeder.schedule(this, writer, programs, transactions);
        coach.initialize(training, events);
        triggers.initialize();
        programController.refresh();
    }
}
