package net.kismetwireless.android.smarterwifimanager.modules;

import net.kismetwireless.android.smarterwifimanager.models.SmarterWorldState;
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
 * Created by dragorn on 12/11/14.
 */
@Module(
        injects = {
                MainActivity.class, ActivityQuickconfig.class,

                SmarterFragment.class, FragmentBluetoothBlacklist.class, FragmentLearned.class, FragmentMain.class, FragmentPrefs.class,
                FragmentSsidBlacklist.class, FragmentTimeRange.class
        },
        complete = false,
        library = true
)
public class WorldStateModule {
    @Provides
    @Singleton
    public SmarterWorldState provideSmarterWorldState() {
        SmarterWorldState worldState = new SmarterWorldState();

        return worldState;
    }
}
