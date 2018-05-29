package net.kismetwireless.android.smarterwifimanager.events;

import android.telephony.CellLocation;

import net.kismetwireless.android.smarterwifimanager.models.CellLocationCommon;

/**
 * Created by dragorn on 11/30/14.
 */
public class EventCellTower {
    private CellLocationCommon locationCommon;

    public EventCellTower(CellLocation l) {
        locationCommon = new CellLocationCommon(l);
    }

    public CellLocationCommon getLocation() {
        return locationCommon;
    }
}
