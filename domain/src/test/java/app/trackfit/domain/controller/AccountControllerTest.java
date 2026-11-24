package app.trackfit.domain.controller;

import static org.junit.Assert.*;

import app.trackfit.domain.model.PersonalRecord;
import app.trackfit.domain.model.Profile;
import app.trackfit.domain.ports.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Test;

public class AccountControllerTest {
    private final TestStore store = new TestStore();
    private final TestTime time = new TestTime();
    private final AccountController controller = new AccountController(store, store, store, store, time, Runnable::run);

    @Test
    public void profileAllowsOptionalFieldsAndDerivesAgeAtBirthdayBoundary() {
        AtomicReference<AccountController.State> state = new AtomicReference<>();
        try (var subscription = controller.state().subscribe(state::set)) {
            assertTrue(controller.refresh().join().saved());
            assertNull(state.get().age());
            var birthday = LocalDate.of(2000, 11, 25);
            var profile = new Profile(" Alex ", Profile.Sex.MALE, birthday, "avatar.image");
            assertTrue(controller.saveProfile(profile).join().saved());
            assertEquals("Alex", state.get().profile().name());
            assertEquals(25, state.get().age().intValue());
            time.now = time.now.plusSeconds(86400);
            controller.refresh().join();
            assertEquals(26, state.get().age().intValue());
            assertEquals(AccountController.Error.FUTURE_BIRTHDAY,
                    controller.saveProfile(new Profile("", Profile.Sex.UNSPECIFIED,
                            LocalDate.of(2027, 1, 1), null)).join().error());
            assertEquals("avatar.image", state.get().profile().avatar());
        }
    }

    @Test
    public void recordEditsKeepDateAndParameterOrderAndDeleteTheRecord() {
        UUID missing = UUID.randomUUID();
        LocalDate original = LocalDate.of(2026, 11, 24);
        var parameters = List.of(new PersonalRecord.Parameter(" Weight ", " 70 kg "),
                new PersonalRecord.Parameter("Reps", "8"));
        assertTrue(controller.saveRecord(null, " Press ", parameters).join().saved());
        var record = store.records().get(0);
        assertEquals(original, record.date());
        assertEquals("Weight", record.parameters().get(0).name());
        assertEquals("Reps", record.parameters().get(1).name());

        time.now = time.now.plusSeconds(86400);
        assertTrue(controller.saveRecord(record.id(), "Press again", parameters).join().saved());
        assertEquals(original, store.records().get(0).date());
        assertEquals(AccountController.Error.RECORD_MISSING, controller.saveRecord(missing, "Press", parameters).join().error());
        assertEquals(AccountController.Error.EMPTY_EXERCISE, controller.saveRecord(null, " ", parameters).join().error());
        assertEquals(AccountController.Error.INVALID_PARAMETER, controller.saveRecord(null,
                "Press", List.of(new PersonalRecord.Parameter("Weight", ""))).join().error());
        assertTrue(controller.deleteRecord(record.id()).join().saved());
        assertTrue(store.records().isEmpty());
        assertTrue(controller.setPushEnabled(false).join().saved());
        assertFalse(store.pushEnabled());
    }

    private static final class TestTime implements TimeSource {
        Instant now = Instant.parse("2026-11-24T10:00:00Z");
        @Override public Instant now() { return now; }
        @Override public ZoneId zone() { return ZoneOffset.UTC; }
    }

    private static final class TestStore implements ProfileRepository, RecordsRepository, SettingsRepository,
            TransactionRunner {
        private final Map<UUID, PersonalRecord> records = new LinkedHashMap<>();
        private Profile profile = Profile.empty();
        private boolean pushEnabled = true;
        @Override public Profile loadProfile() { return profile; }
        @Override public void saveProfile(Profile value) { profile = value; }
        @Override public List<PersonalRecord> records() { return new ArrayList<>(records.values()); }
        @Override public PersonalRecord findRecord(UUID id) { return records.get(id); }
        @Override public void saveRecord(PersonalRecord value) { records.put(value.id(), value); }
        @Override public void deleteRecord(UUID id) { records.remove(id); }
        @Override public boolean pushEnabled() { return pushEnabled; }
        @Override public void setPushEnabled(boolean enabled) { pushEnabled = enabled; }
        @Override public String selectedCoachId() { return null; }
        @Override public void setSelectedCoachId(String id) {}
        @Override public <T> T inTransaction(Supplier<T> command) { return command.get(); }
    }
}
