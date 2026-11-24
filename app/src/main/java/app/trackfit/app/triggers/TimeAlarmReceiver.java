package app.trackfit.app.triggers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.trackfit.infra.alarms.AlarmScheduler;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public final class TimeAlarmReceiver extends BroadcastReceiver {
    @Inject TrainingTriggers triggers;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!AlarmScheduler.ACTION_ALARM.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_DATE_CHANGED.equals(action)) {
            return;
        }
        PendingResult pending = goAsync();
        triggers.receive(intent, pending::finish);
    }
}
