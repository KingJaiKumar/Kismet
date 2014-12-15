package net.kismetwireless.android.smarterwifimanager.modules;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import net.kismetwireless.android.smarterwifimanager.services.SmarterPhoneStateListener;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;
import net.kismetwireless.android.smarterwifimanager.ui.QuickConfigDialog;

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

                MainActivity.class, QuickConfigDialog.class

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
