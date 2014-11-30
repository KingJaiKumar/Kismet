package net.kismetwireless.android.smarterwifimanager;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.modules.ContextModule;
import net.kismetwireless.android.smarterwifimanager.modules.OttobusModule;

/**
 * Created by dragorn on 11/30/14.
 */
final public class Modules {
    static Object[] list(SmarterApplication app) {
        return new Object[] {
                new ContextModule(app),
                new OttobusModule(),
        };
    }

    private Modules() {
        // No instances.
    }
}
