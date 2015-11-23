package net.kismetwireless.android.smarterwifimanager.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

import javax.inject.Inject;

/**
 * Created by dragorn on 10/11/13.
 */
public class AlarmReceiver extends BroadcastReceiver {
    public final static String EXTRA_WIFIDOWN = "EXTRA_SWM_WIFI_DOWN";
    public final static String EXTRA_WIFIUP = "EXTRA_SWM_WIFI_UP";
    public final static String EXTRA_AGGRESSIVE = "EXTRA_AGGRESSIVE";

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Override
    public void onReceive(Context context, Intent intent) {
        SmarterApplication.get(context).inject(this);

        LogAlias.d("smarter-alarmrx", "Got alarm: " + intent.toString() + " " + intent.getExtras().keySet().toString());

        for (String e : intent.getExtras().keySet()) {
            LogAlias.d("smarter-alarmrx", "intent extra " + e);
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Smarter Wi-Fi alarm");


        final boolean triggerShutdown;
        final boolean triggerBringup;
        final boolean triggerAggressive;

        if (intent.hasExtra(EXTRA_WIFIDOWN)) {
            LogAlias.d("smarter-alarmrx", "Got down alarm");
            triggerShutdown = true;
        } else {
            triggerShutdown = false;
        }

        if (intent.hasExtra(EXTRA_WIFIUP)) {
            LogAlias.d("smarter-alarmrx", "Got up alarm");
            triggerBringup = true;
        } else {
            triggerBringup = false;
        }

        if (intent.hasExtra(EXTRA_AGGRESSIVE)) {
            LogAlias.d("smarter-alarmrx", "Got aggressive alarm");
            triggerAggressive = true;
        } else {
            triggerAggressive = false;
        }

        // Make sure we exist before we run
        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            public void run(SmarterWifiServiceBinder b) {
                try {
                    wl.acquire();
                } catch (RuntimeException re) {
                    LogAlias.d("smarter-alarmrx", "tried to lock wl, got runtime exception " + re);
                }

                if (triggerShutdown) {
                    b.getService().doWifiDisable();
                } else if (triggerBringup) {
                    b.getService().doWifiEnable();
                } else if (triggerAggressive) {
                    b.getService().doAggressiveCheck();
                } else {
                    b.configureTimerangeState();
                }

                try {
                    wl.release();
                } catch (RuntimeException re) {
                    LogAlias.d("smarter-rx", "tried to unlock wakelock, got runtime exception: " + re);
                }
            }
        });

    }

    public void setAlarm(Context context, long atTime) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent i = new Intent(context, AlarmReceiver.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            am.setExact(AlarmManager.RTC_WAKEUP, atTime, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, atTime, pi);
        }

        // Log.d("smarter", "setting alarm");
    }


    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}
