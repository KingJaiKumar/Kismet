package net.kismetwireless.android.smarterwifimanager.events;

import android.net.wifi.WifiInfo;

/**
 * Created by dragorn on 11/30/14.
 */
public class EventWifiConnected {
    private WifiInfo wifiInfo;

    public EventWifiConnected(WifiInfo wi) {
        wifiInfo = wi;
    }

    public WifiInfo getWifiInfo() {
        return wifiInfo;
    }
}
