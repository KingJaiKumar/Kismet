package net.kismetwireless.android.smarterwifimanager.events;

import net.kismetwireless.android.smarterwifimanager.models.SmarterTimeRange;

/**
 * Created by dragorn on 12/4/14.
 */
public class EventTimerStarting {
    private SmarterTimeRange timeRange;

    public EventTimerStarting(SmarterTimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public SmarterTimeRange getTimeRange() {
        return timeRange;
    }
}
