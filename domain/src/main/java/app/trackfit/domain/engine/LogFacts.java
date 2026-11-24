package app.trackfit.domain.engine;

import java.time.LocalDate;

@FunctionalInterface
public interface LogFacts {
    int fullCompletionsOn(LocalDate date);
}
