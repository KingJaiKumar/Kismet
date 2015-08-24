package net.kismetwireless.android.smarterwifimanager.modules;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import net.kismetwireless.android.smarterwifimanager.services.SmarterPhoneStateListener;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityBluetoothBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityPrefs;
import net.kismetwireless.android.smarterwifimanager.ui.ActivitySsidBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.ActivitySsidLearned;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityTimeRange;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;
import net.kismetwireless.android.smarterwifimanager.ui.SmarterActivity;
import net.kismetwireless.android.smarterwifimanager.ui.SmarterFragment;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by dragorn on 9/10/14.
 */

@Module(
        injects = {
                SmarterWifiService.class, SmarterWifiServiceBinder.class,

                SmarterPhoneStateListener.class,

                MainActivity.class, ActivityBluetoothBlacklist.class, ActivitySsidBlacklist.class,
                ActivitySsidLearned.class, ActivityTimeRange.class, ActivityPrefs.class,

                SmarterActivity.class, SmarterFragment.class

        },
        complete = false,
        library = true
)
public class OttobusModule {
    @Provides
    @Singleton
    Bus provideBus() {
        return new Bus(ThreadEnforcer.ANY);
    }
}
