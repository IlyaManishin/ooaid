package app.trackfit.app.presentation;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import app.trackfit.app.R;
import app.trackfit.app.triggers.TrainingTriggers;
import app.trackfit.domain.controller.AccountController;
import app.trackfit.domain.controller.Observation;
import app.trackfit.domain.model.PersonalRecord;
import app.trackfit.domain.model.Profile;
import app.trackfit.domain.ports.TimeSource;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.inject.Inject;

@HiltViewModel
public final class AccountViewModel extends ViewModel {
    public record Parameter(String name, String value) {}
    public record Record(UUID id, String exercise, LocalDate date, List<Parameter> parameters) {}
    public record ProfileCard(String name, int sex, LocalDate birthday, Integer age, Bitmap avatar) {}
    public record State(ProfileCard profile, List<Record> records, boolean pushEnabled, boolean loaded) {}

    private final Application app;
    private final AccountController account;
    private final TrainingTriggers triggers;
    private final TimeSource time;
    private final Executor writer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(
            new ProfileCard("", 0, null, null, null), Collections.emptyList(), true, false));
    private final Observation.Subscription subscription;
    private String avatarName;
    private Bitmap cachedAvatar;
    private boolean cleared;

    @Inject
    public AccountViewModel(Application app, AccountController account, TrainingTriggers triggers,
                            TimeSource time, Executor writer) {
        this.app = app;
        this.account = account;
        this.triggers = triggers;
        this.time = time;
        this.writer = writer;
        subscription = account.state().subscribe(data -> writer.execute(() -> map(data)));
    }

    public LiveData<State> state() { return state; }
    public LocalDate today() { return time.now().atZone(time.zone()).toLocalDate(); }
    public void refresh() { account.refresh(); }

    public void saveProfile(String name, int sex, LocalDate birthday, Uri photo,
                            Runnable success, Consumer<String> failure) {
        writer.execute(() -> {
            String old = avatarName;
            String copied;
            try {
                copied = photo == null ? old : importPhoto(photo);
            } catch (IOException | SecurityException error) {
                main.post(() -> {
                    if (!cleared) failure.accept(app.getString(R.string.profile_photo_error));
                });
                return;
            }
            var profile = new Profile(name, Profile.Sex.values()[sex], birthday, copied);
            account.saveProfile(profile).whenComplete((result, error) -> {
                if (photo != null) removeFile(error == null && result.saved() ? old : copied);
                finish(result, error, success, failure);
            });
        });
    }

    public void saveRecord(UUID id, String exercise, List<Parameter> parameters,
                           Runnable success, Consumer<String> failure) {
        var values = new ArrayList<PersonalRecord.Parameter>();
        for (Parameter parameter : parameters) {
            values.add(new PersonalRecord.Parameter(parameter.name(), parameter.value()));
        }
        complete(account.saveRecord(id, exercise, values), success, failure);
    }

    public void deleteRecord(UUID id, Runnable success, Consumer<String> failure) {
        complete(account.deleteRecord(id), success, failure);
    }

    public void setPushEnabled(boolean enabled, Consumer<String> failure) {
        complete(account.setPushEnabled(enabled), triggers::settingsChanged, failure);
    }

    private void map(AccountController.State value) {
        Profile profile = value.profile();
        if (!Objects.equals(avatarName, profile.avatar())) {
            avatarName = profile.avatar();
            cachedAvatar = avatarName == null ? null : decodePhoto(new File(directory(), avatarName));
        }
        var card = new ProfileCard(profile.name(), profile.sex().ordinal(), profile.birthday(), value.age(), cachedAvatar);
        List<Record> records = new ArrayList<>();
        for (var record : value.records()) {
            List<Parameter> parameters = new ArrayList<>();
            for (var parameter : record.parameters()) parameters.add(new Parameter(parameter.name(), parameter.value()));
            records.add(new Record(record.id(), record.exercise(), record.date(), parameters));
        }
        main.post(() -> {
            if (!cleared) state.setValue(new State(card, records, value.pushEnabled(), value.loaded()));
        });
    }

    private String importPhoto(Uri photo) throws IOException {
        File directory = directory();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create avatar directory");
        File target = File.createTempFile("avatar-", ".image", directory);
        try (var input = app.getContentResolver().openInputStream(photo);
             var output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Avatar cannot be opened");
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (IOException | SecurityException error) {
            target.delete();
            throw error;
        }
        if (decodePhoto(target) == null) {
            target.delete();
            throw new IOException("Avatar is not a supported image");
        }
        return target.getName();
    }

    private static Bitmap decodePhoto(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    private File directory() { return new File(app.getFilesDir(), "avatars"); }

    private void removeFile(String file) {
        if (file != null) new File(directory(), file).delete();
    }

    private void complete(CompletableFuture<AccountController.Result> future,
                          Runnable success, Consumer<String> failure) {
        future.whenComplete((result, error) -> finish(result, error, success, failure));
    }

    private void finish(AccountController.Result result, Throwable error, Runnable success, Consumer<String> failure) {
        main.post(() -> {
            if (cleared) return;
            if (error == null && result.saved()) {
                success.run();
                return;
            }
            var problem = error == null ? result.error() : AccountController.Error.STORAGE;
            int message = switch (problem) {
                case FUTURE_BIRTHDAY -> R.string.profile_future_birthday;
                case EMPTY_EXERCISE -> R.string.record_need_exercise;
                case INVALID_PARAMETER -> R.string.record_need_parameter;
                case RECORD_MISSING -> R.string.record_missing;
                case STORAGE -> R.string.error_storage;
            };
            failure.accept(app.getString(message));
        });
    }

    @Override
    protected void onCleared() {
        cleared = true;
        subscription.close();
        main.removeCallbacksAndMessages(null);
    }
}
