package net.kismetwireless.android.smarterwifimanager.modules;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;

import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by dragorn on 9/10/14.
 */

@Module(
        injects = {
                MainActivity.class,

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
