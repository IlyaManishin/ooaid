package app.trackfit.domain.ports;

import app.trackfit.domain.model.PersonalRecord;
import java.util.List;
import java.util.UUID;

public interface RecordsRepository {
    List<PersonalRecord> records();
    PersonalRecord findRecord(UUID id);
    void saveRecord(PersonalRecord record);
    void deleteRecord(UUID id);
}
