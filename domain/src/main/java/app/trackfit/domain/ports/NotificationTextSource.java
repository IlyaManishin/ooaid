package app.trackfit.domain.ports;

import app.trackfit.domain.engine.NotificationPlanner.Kind;

@FunctionalInterface
public interface NotificationTextSource {
    String text(Kind kind);
}
