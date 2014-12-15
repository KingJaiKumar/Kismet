package net.kismetwireless.android.smarterwifimanager.models;

import android.net.wifi.WifiInfo;

/**
 * Created by dragorn on 12/15/14.
 */
public class SmarterWorldState {
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

    public enum WifiState {
        WIFI_BLOCKED, // We're off, period
        WIFI_ON, // We want wifi on
        WIFI_OFF, // we want wifi off
        WIFI_IDLE, // Wifi is on but not connected
        WIFI_IGNORE // Ignoring wifi state
    }

    private CellLocationCommon lastCellLocation;
    private WifiInfo lastWifiInfo;

    private SmarterTimeRange currentTimeRange;
    private SmarterTimeRange nextTimeRange;

    private boolean wifiEnabled = false;
    private boolean bluetoothEnabled = false;

    private ControlType controlType = ControlType.CONTROL_DISABLED;
    private WifiState currentState = WifiState.WIFI_IGNORE;
    private WifiState targetState = WifiState.WIFI_IGNORE;

    public SmarterWorldState() {
        lastCellLocation = null;
        lastWifiInfo = null;
        currentTimeRange = null;
        nextTimeRange = null;
    }

    public CellLocationCommon getLastCellLocation() {
        return lastCellLocation;
    }

    public void setLastCellLocation(CellLocationCommon lastCellLocation) {
        this.lastCellLocation = lastCellLocation;
    }

    public WifiInfo getLastWifiInfo() {
        return lastWifiInfo;
    }

    public void setLastWifiInfo(WifiInfo lastWifiInfo) {
        this.lastWifiInfo = lastWifiInfo;
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
}
