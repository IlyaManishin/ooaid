package app.trackfit.domain.controller;

import java.util.function.Consumer;

public interface Observation<T> {
    Subscription subscribe(Consumer<T> observer);

    interface Subscription extends AutoCloseable {
        @Override void close();
    }
}
