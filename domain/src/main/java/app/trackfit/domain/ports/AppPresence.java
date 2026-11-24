package app.trackfit.domain.ports;

@FunctionalInterface
public interface AppPresence {
    boolean isForeground();
}
