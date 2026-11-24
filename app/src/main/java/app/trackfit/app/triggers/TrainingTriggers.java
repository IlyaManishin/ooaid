package app.trackfit.app.triggers;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import app.trackfit.app.R;
import app.trackfit.domain.controller.CoachController;
import app.trackfit.domain.controller.TrainingController;
import app.trackfit.domain.engine.NotificationPlanner;
import app.trackfit.domain.model.TrainingState;
import app.trackfit.domain.ports.AppPresence;
import app.trackfit.domain.ports.ReminderScheduler;
import app.trackfit.domain.ports.SettingsRepository;
import app.trackfit.domain.ports.TimeSource;
import app.trackfit.infra.alarms.AlarmScheduler;
import app.trackfit.infra.notifications.LocalNotifications;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.time.Duration;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class TrainingTriggers implements DefaultLifecycleObserver, AppPresence {
    private final TrainingController training;
    private final CoachController coach;
    private final SettingsRepository settings;
    private final TimeSource time;
    private final Executor writer;
    private final ReminderScheduler alarms;
    private final LocalNotifications notifications;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable foregroundReconcile = this::reconcile;
    private volatile boolean foreground;
    private volatile TrainingState current = TrainingState.empty();

    @Inject
    public TrainingTriggers(@ApplicationContext Context context, TrainingController training, CoachController coach,
                             SettingsRepository settings, TimeSource time, Executor writer) {
        this.training = training;
        this.coach = coach;
        this.settings = settings;
        this.time = time;
        this.writer = writer;
        alarms = new AlarmScheduler(context, TimeAlarmReceiver.class, time);
        notifications = new LocalNotifications(context,
                kind -> context.getString(kind == NotificationPlanner.Kind.DAILY_REMINDER
                        ? R.string.notif_daily_reminder : R.string.notif_autostart_late),
                context.getString(R.string.notif_channel), R.drawable.ic_tab_progress);
    }

    public void initialize() {
        training.state().subscribe(state -> {
            current = state;
            reschedule();
        });
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        reconcile();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        foreground = true;
        training.reconcile().thenAccept(result -> {
            if (result.succeeded()) {
                coach.appEntered(result.value());
                reschedule();
            }
        });
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        foreground = false;
        main.removeCallbacks(foregroundReconcile);
    }

    @Override public boolean isForeground() { return foreground; }

    public void settingsChanged() {
        reschedule();
    }

    public void receive(Intent intent, Runnable finish) {
        training.reconcile().whenComplete((result, failure) -> {
            try {
                if (failure != null || !result.succeeded()) return;
                var state = result.value();
                var notification = AlarmScheduler.notificationFrom(intent);
                if (notification.isPresent() && NotificationPlanner.shouldShow(notification.get(), state,
                        settings.pushEnabled(), foreground, time.now(), time.zone())) {
                    notifications.show(notification.get());
                }
                current = state;
                reschedule();
            } finally {
                finish.run();
            }
        });
    }

    private void reconcile() {
        training.reconcile().thenAccept(result -> {
            if (result.succeeded()) {
                current = result.value();
                reschedule();
            }
        });
    }

    private void reschedule() {
        writer.execute(() -> {
            TrainingState snapshot = current;
            var now = time.now();
            var next = training.nextTransitionAt(snapshot);
            alarms.replaceAll(NotificationPlanner.plan(snapshot, settings.pushEnabled(), now, time.zone()), next);
            main.post(() -> {
                main.removeCallbacks(foregroundReconcile);
                if (foreground) {
                    next.ifPresent(at -> main.postDelayed(foregroundReconcile,
                            Math.max(1, Duration.between(time.now(), at).toMillis())));
                }
            });
        });
    }
}
