package net.kismetwireless.android.smarterwifimanager;

import android.app.Application;
import android.content.Context;

import dagger.ObjectGraph;

/**
 * Created by dragorn on 9/10/14.
 */
public class SmarterApplication extends Application {
    private ObjectGraph objectGraph;

    @Override
    public void onCreate() {
        super.onCreate();

        LogAlias.d("appstore", "creating app object graph");
        objectGraph = ObjectGraph.create(Modules.list(this));
        objectGraph.inject(this);
    }

    public void inject(Object o) {
        objectGraph.inject(o);
    }

    public static SmarterApplication get(Context context) {
        return (SmarterApplication) context.getApplicationContext();
    }
}
