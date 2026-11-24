package app.trackfit.app;

import android.app.Application;
import app.trackfit.domain.controller.ProgramController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import dagger.hilt.android.HiltAndroidApp;
import java.util.concurrent.Executor;
import javax.inject.Inject;

@HiltAndroidApp
public final class TrackFitApplication extends Application {
    @Inject Executor writer;
    @Inject ProgramRepository programs;
    @Inject TransactionRunner transactions;
    @Inject ProgramController programController;
    @Inject TrainingController trainingController;

    @Override
    public void onCreate() {
        super.onCreate();
        StartupSeeder.schedule(this, writer, programs, transactions);
        trainingController.reconcile();
        programController.refresh();
    }
}
