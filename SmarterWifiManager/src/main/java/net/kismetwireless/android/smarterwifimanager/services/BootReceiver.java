package net.kismetwireless.android.smarterwifimanager.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by dragorn on 9/26/13.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

        if (p.getBoolean("start_boot", true)) {
            SmarterWifiServiceBinder b = new SmarterWifiServiceBinder(context);
            b.doStartService();
        }
    }
}
