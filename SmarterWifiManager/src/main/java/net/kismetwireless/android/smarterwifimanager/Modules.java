package net.kismetwireless.android.smarterwifimanager;

import net.kismetwireless.android.smarterwifimanager.modules.ContextModule;
import net.kismetwireless.android.smarterwifimanager.modules.DatabaseModule;
import net.kismetwireless.android.smarterwifimanager.modules.OttobusModule;
import net.kismetwireless.android.smarterwifimanager.modules.SmarterServiceModule;

/**
 * Created by dragorn on 11/30/14.
 */
final public class Modules {
    static Object[] list(SmarterApplication app) {
        return new Object[] {
                new ContextModule(app),
                new OttobusModule(),
                new SmarterServiceModule(),
                new DatabaseModule()
        };
    }

    private Modules() {
        // No instances.
    }
}
