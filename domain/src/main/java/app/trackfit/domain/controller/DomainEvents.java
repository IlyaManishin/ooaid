package app.trackfit.domain.controller;

import app.trackfit.domain.engine.DomainEvent;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DomainEvents implements Observation<DomainEvent> {
    private final CopyOnWriteArrayList<Consumer<DomainEvent>> observers = new CopyOnWriteArrayList<>();

    @Override
    public Subscription subscribe(Consumer<DomainEvent> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    void publish(DomainEvent event) {
        for (Consumer<DomainEvent> observer : observers) {
            observer.accept(event);
        }
    }
}
