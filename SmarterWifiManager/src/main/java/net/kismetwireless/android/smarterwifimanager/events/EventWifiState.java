package net.kismetwireless.android.smarterwifimanager.events;

/**
 * Created by dragorn on 12/15/14.
 */
public class EventWifiState {
    private boolean isEnabled = false;

    public EventWifiState(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
