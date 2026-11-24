package app.trackfit.infra.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import app.trackfit.domain.engine.NotificationPlanner.Kind;
import app.trackfit.domain.engine.NotificationPlanner.PlannedNotification;
import app.trackfit.domain.ports.ReminderScheduler;
import app.trackfit.domain.ports.TimeSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AlarmScheduler implements ReminderScheduler {
    public static final String ACTION_ALARM = "app.trackfit.action.ALARM";
    private static final String TRANSITION = "transition";
    private static final String KIND = "kind";
    private static final String DATE = "date";
    private static final String AT = "at";
    private static final String RUN = "run";
    private static final String CYCLE = "cycle";
    private final Context context;
    private final Class<?> receiver;
    private final AlarmManager alarms;
    private final SharedPreferences scheduled;
    private final TimeSource time;

    public AlarmScheduler(Context context, Class<?> receiver, TimeSource time) {
        this.context = context;
        this.receiver = receiver;
        this.time = time;
        alarms = context.getSystemService(AlarmManager.class);
        scheduled = context.getSharedPreferences("trackfit_alarms", Context.MODE_PRIVATE);
    }

    @Override
    public synchronized void replaceAll(List<PlannedNotification> plan, Optional<Instant> nextReconcile) {
        Map<String, Long> targets = new LinkedHashMap<>();
        nextReconcile.ifPresent(at -> targets.put(TRANSITION, at.toEpochMilli()));
        for (var notification : plan) targets.put(notification.id(), notification.at().toEpochMilli());
        SharedPreferences.Editor editor = scheduled.edit();
        long now = time.now().toEpochMilli();
        for (var previous : scheduled.getAll().entrySet()) {
            if (targets.containsKey(previous.getKey())) continue;
            if (previous.getKey().equals(TRANSITION) || (long) previous.getValue() > now) {
                PendingIntent pending = find(previous.getKey());
                if (pending != null) {
                    alarms.cancel(pending);
                    pending.cancel();
                }
            }
            editor.remove(previous.getKey());
        }
        nextReconcile.ifPresent(at -> schedule(intent(TRANSITION).putExtra(AT, at.toEpochMilli()), at, editor));
        for (var notification : plan) {
            Intent intent = intent(notification.id()).putExtra(KIND, notification.kind().name())
                    .putExtra(DATE, notification.date().toString()).putExtra(AT, notification.at().toEpochMilli())
                    .putExtra(RUN, notification.runId().toString()).putExtra(CYCLE, notification.cycle());
            schedule(intent, notification.at(), editor);
        }
        editor.apply();
    }

    public static Optional<PlannedNotification> notificationFrom(Intent intent) {
        String kind = intent.getStringExtra(KIND);
        if (kind == null || intent.getData() == null) return Optional.empty();
        return Optional.of(new PlannedNotification(intent.getData().getLastPathSegment(), Kind.valueOf(kind),
                LocalDate.parse(intent.getStringExtra(DATE)), Instant.ofEpochMilli(intent.getLongExtra(AT, 0)),
                UUID.fromString(intent.getStringExtra(RUN)), intent.getIntExtra(CYCLE, 0)));
    }

    private void schedule(Intent intent, Instant at, SharedPreferences.Editor editor) {
        String id = intent.getData().getLastPathSegment();
        long millis = at.toEpochMilli();
        long previous = scheduled.getLong(id, Long.MIN_VALUE);
        if (previous == millis && find(id) != null) return;
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            } catch (SecurityException permissionChanged) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
            }
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending);
        }
        editor.putLong(id, millis);
    }

    private PendingIntent find(String id) {
        return PendingIntent.getBroadcast(context, 0, intent(id), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent intent(String id) {
        return new Intent(context, receiver).setAction(ACTION_ALARM).setData(new Uri.Builder().scheme("trackfit").authority("alarm")
                .appendPath(id).build());
    }
}
