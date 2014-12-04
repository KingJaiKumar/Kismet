package net.kismetwireless.android.smarterwifimanager.modules;

import android.content.Context;

import net.kismetwireless.android.smarterwifimanager.services.AlarmReceiver;
import net.kismetwireless.android.smarterwifimanager.services.BootReceiver;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityQuickconfig;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentBluetoothBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentLearned;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentMain;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentPrefs;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentSsidBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentTimeRange;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;
import net.kismetwireless.android.smarterwifimanager.ui.SmarterFragment;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by dragorn on 10/8/14.
 */

@Module(
        injects = {
                AlarmReceiver.class, BootReceiver.class,

                MainActivity.class, ActivityQuickconfig.class,

                SmarterFragment.class, FragmentBluetoothBlacklist.class, FragmentLearned.class, FragmentMain.class, FragmentPrefs.class,
                FragmentSsidBlacklist.class, FragmentTimeRange.class
        },
        includes = {
                ContextModule.class
        },
        complete = false,
        library = true
)
public class SmarterServiceModule {
    @Provides
    @Singleton
    public SmarterWifiServiceBinder provideStoreServiceBinder(Context c) {
        SmarterWifiServiceBinder serviceBinder = new SmarterWifiServiceBinder(c);
        serviceBinder.doBindService();

        return serviceBinder;
    }

}
