package app.trackfit.domain.ports;

import app.trackfit.domain.coach.CoachMemory;

public interface CoachMemoryStore {
    CoachMemory loadCoachMemory();
    void saveCoachMemory(CoachMemory memory);
}
