package app.trackfit.domain.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class StatePublisher<T> implements Observation<T> {
    private final List<Consumer<T>> observers = new ArrayList<>();
    private T value;

    StatePublisher(T initial) {
        value = initial;
    }

    @Override
    public synchronized Subscription subscribe(Consumer<T> observer) {
        observers.add(observer);
        observer.accept(value);
        return () -> remove(observer);
    }

    private synchronized void remove(Consumer<T> observer) {
        observers.remove(observer);
    }

    synchronized void update(T replacement) {
        if (Objects.equals(value, replacement)) return;
        value = replacement;
        for (Consumer<T> observer : new ArrayList<>(observers)) {
            observer.accept(value);
        }
    }
}
