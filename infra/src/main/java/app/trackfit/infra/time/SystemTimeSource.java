package app.trackfit.infra.time;

import app.trackfit.domain.ports.TimeSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class SystemTimeSource implements TimeSource {
    @Override public Instant now() { return Instant.now().truncatedTo(ChronoUnit.MILLIS); }
    @Override public ZoneId zone() { return ZoneId.systemDefault(); }
}
