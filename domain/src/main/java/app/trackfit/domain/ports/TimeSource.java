package app.trackfit.domain.ports;

import java.time.Instant;
import java.time.ZoneId;

public interface TimeSource {
    Instant now();
    ZoneId zone();
}
