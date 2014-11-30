package net.kismetwireless.android.smarterwifimanager.modules;

/**
 * Created by dragorn on 10/22/14.
 */

import android.content.Context;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;

import dagger.Module;
import dagger.Provides;

@Module(
        injects = {
                SmarterApplication.class,

                MainActivity.class,
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
