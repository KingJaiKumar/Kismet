package net.kismetwireless.android.smarterwifimanager.modules;

import android.content.Context;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.models.TimeCardAdapter;
import net.kismetwireless.android.smarterwifimanager.services.AlarmReceiver;
import net.kismetwireless.android.smarterwifimanager.services.BootReceiver;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityBluetoothBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityPrefs;
import net.kismetwireless.android.smarterwifimanager.ui.ActivitySsidBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.ActivitySsidLearned;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityTimeRange;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentBluetoothBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentLearned;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentMain;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentPrefs;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentSsidBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.FragmentTimeRange;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;
import net.kismetwireless.android.smarterwifimanager.ui.SmarterActivity;
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

                SmarterFragment.class, SmarterActivity.class,

                MainActivity.class, ActivityBluetoothBlacklist.class, ActivitySsidBlacklist.class,
                ActivitySsidLearned.class, ActivityTimeRange.class, ActivityPrefs.class,

                FragmentBluetoothBlacklist.class, FragmentLearned.class, FragmentMain.class, FragmentPrefs.class,
                FragmentSsidBlacklist.class, FragmentTimeRange.class,

                TimeCardAdapter.class
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
        LogAlias.d("smarter", "Making new service binder");
        SmarterWifiServiceBinder serviceBinder = new SmarterWifiServiceBinder(c);
        serviceBinder.doBindService();

        return serviceBinder;
    }

}
