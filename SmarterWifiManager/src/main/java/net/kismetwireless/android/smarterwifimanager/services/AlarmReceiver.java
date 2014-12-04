package net.kismetwireless.android.smarterwifimanager.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

import javax.inject.Inject;

/**
 * Created by dragorn on 10/11/13.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Override
    public void onReceive(Context context, Intent intent) {
        SmarterApplication.get(context).inject(this);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Smarter Wi-Fi Timerange");
        wl.acquire();

        // Make sure we exist before we run
        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            public void run(SmarterWifiServiceBinder b) {
                b.configureTimerangeState();
                wl.release();
            }
        });

    }

    public void setAlarm(Context context, long atTime) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.set(AlarmManager.RTC_WAKEUP, atTime, pi);

        // Log.d("smarter", "setting alarm");
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}
