package app.trackfit.domain.coach;

import java.time.Instant;

public record CoachMemory(Instant lastLineAt, String motivatedSession) {
    public static CoachMemory empty() {
        return new CoachMemory(null, null);
    }
}
