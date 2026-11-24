package app.trackfit.infra.notifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import app.trackfit.domain.engine.NotificationPlanner.PlannedNotification;
import app.trackfit.domain.ports.NotificationTextSource;

public final class LocalNotifications {
    private static final String CHANNEL = "workout_reminders";
    private final Context context;
    private final NotificationManager manager;
    private final NotificationTextSource texts;
    private final int icon;

    public LocalNotifications(Context context, NotificationTextSource texts, String channelName, int icon) {
        this.context = context;
        this.texts = texts;
        this.icon = icon;
        manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, channelName, NotificationManager.IMPORTANCE_DEFAULT));
    }

    public void show(PlannedNotification notification) {
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            return;
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        Notification.Builder message = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(icon).setContentText(texts.text(notification.kind())).setAutoCancel(true);
        if (launch != null) message.setContentIntent(PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE));
        manager.notify(notification.id(), 1, message.build());
    }
}
