package app.trackfit.app;

import android.content.Context;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import java.util.concurrent.Executor;

final class StartupSeeder {
    private StartupSeeder() {}

    static void schedule(Context context, Executor writer, ProgramRepository programs, TransactionRunner transactions) {}
}
