package net.kismetwireless.android.smarterwifimanager.events;

/**
 * Created by dragorn on 11/30/14.
 */
public class EventCellTower {
    private long towerid;

    public EventCellTower(long id) {
        towerid = id;
    }

    public long getTowerid() {
        return towerid;
    }
}
