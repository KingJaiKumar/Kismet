package net.kismetwireless.android.smarterwifimanager.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/26/13.
 */
public class BootReceiver extends BroadcastReceiver {
    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Override
    public void onReceive(Context context, Intent intent) {
        SmarterApplication.get(context).inject(this);

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

        if (p.getBoolean("start_boot", true)) {
            serviceBinder.doStartService();
        }
    }
}
