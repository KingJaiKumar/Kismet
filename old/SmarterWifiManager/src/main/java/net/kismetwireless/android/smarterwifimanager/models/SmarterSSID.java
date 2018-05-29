package net.kismetwireless.android.smarterwifimanager.models;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.List;

/**
 * Created by dragorn on 9/18/13.
 *
 * Conflation of SSID data for blacklist, etc
 *
 * Slightly bad behavior - if not loaded from blacklist, does not contain valid blacklist data,
 * blacklist SSIDs are stored quotes-buffered because of the wifi API
 *
 */
public class SmarterSSID {
    public static int CRYPT_UNKNOWN = 0;
    public static int CRYPT_OPEN = 1;
    public static int CRYPT_WEP = 2;
    public static int CRYPT_GOOD = 3;

    private String ssid;
    private boolean blacklisted;
    private long bldbid;
    private int numtowers;
    private int numbssids;
    private long mapdbid;
    private int crypt;
    private String bssid;

    public SmarterSSID() {
        crypt = CRYPT_UNKNOWN;
        mapdbid = -1;
        bldbid = -1;
    }

    public SmarterSSID(String ssid, boolean blacklisted, long bldb) {
        crypt = CRYPT_UNKNOWN;
        this.ssid = ssid;
        this.blacklisted = blacklisted;
        bldbid = bldb;

        mapdbid = -1;
    }

    // Create using the current SSID
    public SmarterSSID(WifiManager m, SmarterDBSource dbSource) {
        crypt = CRYPT_UNKNOWN;

        WifiInfo wi = m.getConnectionInfo();
        int ni = wi.getNetworkId();
        List<WifiConfiguration> wcl = m.getConfiguredNetworks();

        setBssid(wi.getBSSID());
        setSsid((wi.getSSID()));

        for (WifiConfiguration c : wcl) {
            if (c.networkId == ni) {
                if (c.allowedKeyManagement.equals(WifiConfiguration.KeyMgmt.NONE)) {
                    crypt = CRYPT_OPEN;
                }

                if (c.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP40) ||
                        c.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP104)) {
                    crypt = CRYPT_WEP;
                }

                if (c.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.CCMP) ||
                        c.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.TKIP)) {
                    crypt = CRYPT_GOOD;
                }

                break;
            }
        }

        dbSource.fillSsidBlacklisted(this);
    }

    public SmarterSSID(String ssid, int numtowers, long mapdbid) {
        crypt = CRYPT_UNKNOWN;
        this.ssid = ssid;
        this.numtowers = numtowers;
        this.mapdbid = mapdbid;

        bldbid = -1;
    }

    public SmarterSSID(String ssid, int numbssids) {
        crypt = CRYPT_UNKNOWN;
        this.ssid = ssid;
        this.numbssids = numbssids;
        this.mapdbid = -1;
        this.bldbid = -1;
    }

    public void setCrypt(int c) { crypt = c; }
    public int getCrypt() { return crypt; }
    public boolean isEncrypted() { return crypt == CRYPT_GOOD; }
    public boolean isOpen() { return crypt != CRYPT_GOOD; }

    public void setSsid(String s) {
        ssid = s;
    }
    public String getSsid() {
        return ssid;
    }

    public String getDisplaySsid() {
        if (ssid.length() > 1) {
           if (ssid.charAt(0) == '\"' && ssid.charAt(ssid.length() - 1) == '\"')
               return ssid.substring(1, ssid.length() - 1);
        }

        return ssid;
    }

    public void setBssid(String b) { bssid = b; }
    public String getBssid() { return bssid; }

    public void setNumTowers(int nt) {
        numtowers = nt;
    }

    public void setNumBssids(int nb) { numbssids = nb; }

    public void setMapDbId(long id) {
        mapdbid = id;
    }

    public int getNumTowers() {
        return numtowers;
    }

    public int getNumBssids() { return numbssids; }

    public long getMapDbId() {
        return mapdbid;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public long getBlacklistDatabaseId() {
        return bldbid;
    }

    public void setBlacklisted(boolean b) {
        blacklisted = b;
    }

    public void setBlacklistDatabaseId(long i) {
        bldbid = i;
    }

    public boolean equals(SmarterSSID e) {
        return (ssid.equals(e.getSsid()) && blacklisted == e.isBlacklisted());
    }

}
