package app.trackfit.app.triggers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public final class TimeAlarmReceiver extends BroadcastReceiver {
    @Inject TrainingTriggers triggers;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        triggers.receive(intent, pending::finish);
    }
}
