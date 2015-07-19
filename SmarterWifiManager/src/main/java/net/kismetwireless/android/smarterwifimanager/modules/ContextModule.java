package net.kismetwireless.android.smarterwifimanager.modules;

/**
 * Created by dragorn on 10/22/14.
 */

import android.content.Context;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.models.SmarterWorldState;
import net.kismetwireless.android.smarterwifimanager.models.TimeCardAdapter;
import net.kismetwireless.android.smarterwifimanager.services.NetworkReceiver;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityBluetoothBlacklist;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityPrefs;
import net.kismetwireless.android.smarterwifimanager.ui.ActivityQuickconfig;
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

import dagger.Module;
import dagger.Provides;

@Module(
        injects = {
                SmarterApplication.class,

                SmarterWorldState.class,

                NetworkReceiver.class,

                SmarterFragment.class, SmarterActivity.class,

                MainActivity.class, ActivityBluetoothBlacklist.class, ActivityQuickconfig.class, ActivitySsidBlacklist.class,
                ActivitySsidLearned.class, ActivityTimeRange.class, ActivityPrefs.class,

                FragmentBluetoothBlacklist.class, FragmentLearned.class, FragmentMain.class, FragmentPrefs.class,
                FragmentSsidBlacklist.class, FragmentTimeRange.class,

                TimeCardAdapter.class
        },
        complete = false,
        library = true
)
public class ContextModule {
    private Context context;

    public ContextModule(Context c) {
        context = c;
    }

    @Provides
    public Context provideContext() {
        return this.context;
    }
}
