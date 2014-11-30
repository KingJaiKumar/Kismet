package net.kismetwireless.android.smarterwifimanager.modules;

import android.content.Context;

import net.kismetwireless.android.smarterwifimanager.services.AlarmReceiver;
import net.kismetwireless.android.smarterwifimanager.services.BootReceiver;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by dragorn on 10/8/14.
 */

@Module(
        injects = {
                AlarmReceiver.class, BootReceiver.class
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
