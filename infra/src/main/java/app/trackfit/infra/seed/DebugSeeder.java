package app.trackfit.infra.seed;

import app.trackfit.domain.model.CycleDay;
import app.trackfit.domain.model.DayId;
import app.trackfit.domain.model.Exercise;
import app.trackfit.domain.model.ExerciseId;
import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import app.trackfit.domain.ports.ProgramRepository;
import app.trackfit.domain.ports.TransactionRunner;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DebugSeeder {
    private DebugSeeder() {}

    public static void seed(ProgramRepository programs, TransactionRunner transactions,
                            List<String> names, String workout, String exercise) {
        transactions.inTransaction(() -> {
            if (programs.programs().isEmpty()) {
                for (String name : names) {
                    var movement = new Exercise.Strength(new ExerciseId(UUID.randomUUID()), exercise,
                            null, 3, 10, null, null);
                    var work = new CycleDay.Work(new DayId(UUID.randomUUID()), workout, 30,
                            null, Collections.singletonList(movement));
                    var rest = new CycleDay.Rest(new DayId(UUID.randomUUID()));
                    programs.save(new Program(new ProgramId(UUID.randomUUID()), name, Arrays.asList(work, rest)));
                }
            }
            return null;
        });
    }
}
