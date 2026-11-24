package app.trackfit.domain.ports;

import app.trackfit.domain.model.Program;
import app.trackfit.domain.model.ProgramId;
import java.util.List;
import java.util.Optional;

public interface ProgramRepository {
    List<Program> programs();
    Optional<Program> load(ProgramId id);
    void save(Program program);
}
