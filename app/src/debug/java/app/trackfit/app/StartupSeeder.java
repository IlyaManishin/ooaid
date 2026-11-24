package app.trackfit.app;

import android.content.Context;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import app.trackfit.infra.seed.DebugSeeder;
import java.util.Arrays;
import java.util.concurrent.Executor;

final class StartupSeeder {
    private StartupSeeder() {}

    static void schedule(Context context, Executor writer, ProgramRepository programs, TransactionRunner transactions) {
        if (BuildConfig.SEED_EXAMPLES) {
            writer.execute(() -> DebugSeeder.seed(programs, transactions,
                    Arrays.asList(context.getString(R.string.seed_strength), context.getString(R.string.seed_legs),
                            context.getString(R.string.seed_fitness)), context.getString(R.string.seed_workout),
                    context.getString(R.string.seed_exercise)));
        }
    }
}
