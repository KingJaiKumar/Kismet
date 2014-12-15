package net.kismetwireless.android.smarterwifimanager.events;

import net.kismetwireless.android.smarterwifimanager.models.SmarterTimeRange;

/**
 * Created by dragorn on 12/4/14.
 */
public class EventTimerEnding {
    private SmarterTimeRange timeRange;

    public EventTimerEnding(SmarterTimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public SmarterTimeRange getTimeRange() {
        return timeRange;
    }
}
