package net.kismetwireless.android.smarterwifimanager.models;

import android.content.Context;
import android.net.wifi.WifiInfo;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

import javax.inject.Inject;

/**
 * Created by dragorn on 12/15/14.
 */
public class SmarterWorldState {
    // SWS is a singleton so this isn't horrible
    @Inject
    SmarterDBSource dbSource;

    @Inject
    Context context;

    // Control type - what we want to do (and what state we're in now)
    public enum ControlType {
        CONTROL_DISABLED, // We don't control device state
        CONTROL_USER, // User has taken control
        CONTROL_TOWER, // Learned tower
        CONTROL_TOWERID, // Blacklisted tower, currently not used
        CONTROL_GEOFENCE, // Geofence, currently not used
        CONTROL_BLUETOOTH, // Connected to controlling BT device
        CONTROL_TIME, // Within a timerange
        CONTROL_SSIDBLACKLIST, // Connected to a blacklisted SSID
        CONTROL_AIRPLANE, // In airplane mode
        CONTROL_TETHER, // In tether mode
        CONTROL_NEVERRUN // Never run, do nothing until user talks to us
    }

    // The state the wifi is in (combination of current state and target state)
    public enum WifiState {
        WIFI_BLOCKED, // We're off, period
        WIFI_ON, // We want wifi on
        WIFI_OFF, // we want wifi off
        WIFI_IDLE, // Wifi is on but not connected
        WIFI_IGNORE // Ignoring wifi state
    }

    private CellLocationCommon cellLocation = new CellLocationCommon();
    private CellLocationCommon.TowerType cellTowerType = CellLocationCommon.TowerType.TOWER_UNKNOWN;
    private WifiInfo wifiInfo = null;

    private CellLocationCommon previousCellLocation = null;
    private WifiInfo previousWifiInfo = null;

    private SmarterTimeRange currentTimeRange = null;
    private SmarterTimeRange nextTimeRange = null;

    private boolean wifiEnabled = false;
    private boolean bluetoothEnabled = false;

    private ControlType controlType = ControlType.CONTROL_DISABLED;
    private WifiState currentState = WifiState.WIFI_IGNORE;
    private WifiState targetState = WifiState.WIFI_IGNORE;

    public SmarterWorldState(Context c) {
        SmarterApplication.get(c).inject(this);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (cellLocation != null) {
            sb.append("CELL: ");
            sb.append(cellLocation.toString());
            sb.append("\n");
        } else {
            sb.append("NO CELL\n");
        }

        if (wifiInfo != null) {
            sb.append("WIFI: ");
            sb.append(wifiInfo.toString());
            sb.append("\n");
        } else {
            sb.append("NO WIFI\n");
        }

        return sb.toString();
    }

    public CellLocationCommon getCellLocation() {
        return cellLocation;
    }

    public void setCellLocation(CellLocationCommon cellLocation) {
        this.previousCellLocation = this.cellLocation;

        this.cellLocation = cellLocation;

        if (cellLocation.isValid()) {
            if (dbSource.queryTowerMapped(this.getCellLocation().getTowerId())) {
                this.cellLocation.setTowerEnabled(true);
            }
        }
    }

    public WifiInfo getWifiInfo() {
        return wifiInfo;
    }

    public void setWifiInfo(WifiInfo wifiInfo) {
        this.previousWifiInfo = this.wifiInfo;
        this.wifiInfo = wifiInfo;
    }

    public SmarterTimeRange getCurrentTimeRange() {
        return currentTimeRange;
    }

    public void setCurrentTimeRange(SmarterTimeRange currentTimeRange) {
        this.currentTimeRange = currentTimeRange;
    }

    public SmarterTimeRange getNextTimeRange() {
        return nextTimeRange;
    }

    public void setNextTimeRange(SmarterTimeRange nextTimeRange) {
        this.nextTimeRange = nextTimeRange;
    }

    public boolean isWifiEnabled() {
        return wifiEnabled;
    }

    public void setWifiEnabled(boolean wifiEnabled) {
        this.wifiEnabled = wifiEnabled;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothEnabled;
    }

    public void setBluetoothEnabled(boolean bluetoothEnabled) {
        this.bluetoothEnabled = bluetoothEnabled;
    }

    public ControlType getControlType() {
        return controlType;
    }

    public void setControlType(ControlType controlType) {
        this.controlType = controlType;
    }

    public WifiState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(WifiState currentState) {
        this.currentState = currentState;
    }

    public WifiState getTargetState() {
        return targetState;
    }

    public void setTargetState(WifiState targetState) {
        this.targetState = targetState;
    }

    public CellLocationCommon.TowerType getCellTowerType() {
        return cellTowerType;
    }

    public void setCellTowerType(CellLocationCommon.TowerType cellTowerType) {
        this.cellTowerType = cellTowerType;
    }
}
