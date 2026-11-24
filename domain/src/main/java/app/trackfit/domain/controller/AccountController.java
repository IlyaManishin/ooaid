package app.trackfit.domain.controller;

import app.trackfit.domain.model.PersonalRecord;
import app.trackfit.domain.model.Profile;
import app.trackfit.domain.ports.ProfileRepository;
import app.trackfit.domain.ports.RecordsRepository;
import app.trackfit.domain.ports.SettingsRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.domain.ports.TransactionRunner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class AccountController {
    public enum Error { STORAGE, FUTURE_BIRTHDAY, EMPTY_EXERCISE, INVALID_PARAMETER, RECORD_MISSING }
    public record Result(Error error) {
        public boolean saved() { return error == null; }
    }
    public record State(Profile profile, Integer age, List<PersonalRecord> records, boolean pushEnabled, boolean loaded) {}

    private final ProfileRepository profiles;
    private final RecordsRepository records;
    private final SettingsRepository settings;
    private final TransactionRunner transactions;
    private final TimeSource time;
    private final Executor writer;
    private final StatePublisher<State> state = new StatePublisher<>(new State(
            Profile.empty(), null, Collections.emptyList(), true, false));

    public AccountController(ProfileRepository profiles, RecordsRepository records, SettingsRepository settings,
                             TransactionRunner transactions, TimeSource time, Executor writer) {
        this.profiles = profiles;
        this.records = records;
        this.settings = settings;
        this.transactions = transactions;
        this.time = time;
        this.writer = writer;
    }

    public Observation<State> state() { return state; }

    public CompletableFuture<Result> refresh() {
        return submit(() -> null);
    }

    public CompletableFuture<Result> saveProfile(Profile profile) {
        return submit(() -> {
            if (profile.birthday() != null && profile.birthday().isAfter(today())) return Error.FUTURE_BIRTHDAY;
            profiles.saveProfile(new Profile(profile.name().trim(), profile.sex(), profile.birthday(), profile.avatar()));
            return null;
        });
    }

    public CompletableFuture<Result> saveRecord(UUID id, String exercise, List<PersonalRecord.Parameter> parameters) {
        return submit(() -> {
            if (exercise.trim().isEmpty()) return Error.EMPTY_EXERCISE;
            List<PersonalRecord.Parameter> normalized = new ArrayList<>();
            for (var parameter : parameters) {
                if (parameter.name().trim().isEmpty() || parameter.value().trim().isEmpty()) {
                    return Error.INVALID_PARAMETER;
                }
                normalized.add(new PersonalRecord.Parameter(parameter.name().trim(), parameter.value().trim()));
            }
            return transactions.inTransaction(() -> {
                PersonalRecord previous = id == null ? null : records.findRecord(id);
                if (id != null && previous == null) return Error.RECORD_MISSING;
                records.saveRecord(new PersonalRecord(id == null ? UUID.randomUUID() : id,
                        exercise.trim(), previous == null ? today() : previous.date(), normalized));
                return null;
            });
        });
    }

    public CompletableFuture<Result> deleteRecord(UUID id) {
        return submit(() -> {
            records.deleteRecord(id);
            return null;
        });
    }

    public CompletableFuture<Result> setPushEnabled(boolean enabled) {
        return submit(() -> {
            settings.setPushEnabled(enabled);
            return null;
        });
    }

    private CompletableFuture<Result> submit(Supplier<Error> command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Error error = command.get();
                if (error == null) {
                    Profile profile = profiles.loadProfile();
                    state.update(new State(profile, profile.ageOn(today()),
                            Collections.unmodifiableList(new ArrayList<>(records.records())), settings.pushEnabled(), true));
                }
                return new Result(error);
            } catch (RuntimeException error) {
                return new Result(Error.STORAGE);
            }
        }, writer);
    }

    private LocalDate today() {
        return time.now().atZone(time.zone()).toLocalDate();
    }
}
