package app.trackfit.domain.ports;

import app.trackfit.domain.engine.NotificationPlanner.PlannedNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReminderScheduler {
    void replaceAll(List<PlannedNotification> plan, Optional<Instant> nextReconcile);
}
