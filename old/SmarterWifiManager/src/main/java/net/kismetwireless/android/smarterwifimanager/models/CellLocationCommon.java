package net.kismetwireless.android.smarterwifimanager.models;

import android.telephony.CellLocation;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import net.kismetwireless.android.smarterwifimanager.LogAlias;

import java.util.Date;

/**
 * Created by dragorn on 8/30/13.
 *
 * Mangle various cell location types
 */
public class CellLocationCommon {
    public enum TowerType {
        TOWER_UNKNOWN, // Unknown tower, nothing to do
        TOWER_BLOCK, // Known block tower, currently unimplemented
        TOWER_ENABLE, // Known enable tower, we want to turn on wifi
        TOWER_INVALID, // Bad tower
    };

    public enum LocationType {
        GSM, CDMA
    };

    private long towerId;
    private boolean valid = true;

    private LocationType type = LocationType.GSM;

    private TowerType towerType = TowerType.TOWER_UNKNOWN;

    private long seenTime;

    // "halfbad" towers look weird but we do what we can with them
    private boolean halfbad = false;

    public CellLocationCommon() {
        valid = false;
        towerId = -1;
        seenTime = System.currentTimeMillis();
    }

    public CellLocationCommon(CellLocation l) {
        if (l == null) {
            valid = false;
        } else if (l instanceof GsmCellLocation) {
            // LogAlias.d("smarter", "Looks like a GSM location");
            setGsmLocation((GsmCellLocation) l);
        } else if (l instanceof CdmaCellLocation) {
            setCdmaLocation((CdmaCellLocation) l);
        }

        seenTime = System.currentTimeMillis();
    }

    public void setGsmLocation(GsmCellLocation gsm) {
        // LogAlias.d("smarter", "gsm lac " + gsm.getLac() + " cid " + gsm.getCid() + " psc " + gsm.getPsc());

        type = LocationType.GSM;

        if (gsm.getLac() < 0 && gsm.getCid() < 0) {
            LogAlias.d("smarter", "gsm tower lac and cid negative, invalid result");
            valid = false;
            towerId = -1;
            return;
        }

        // Combine lac and cid for track purposes
        towerId = ((long) gsm.getLac() << 32) + (long) gsm.getCid();

        if (towerId < 0) {
            LogAlias.d("smarter", "gsm tower problem:  valid tower lac " + gsm.getLac() + " cid " + gsm.getCid() + " but negative result, kluging to positive");
            towerId = Math.abs(towerId);
            valid = true;
            halfbad = true;
        }

        // LogAlias.d("smarter", "towerid " + towerId);
    }

    public CellLocationCommon(GsmCellLocation gsm) {
        setGsmLocation(gsm);
    }

    public void setCdmaLocation(CdmaCellLocation cdma) {
        type = LocationType.CDMA;

        if (cdma.getNetworkId() < 0 && cdma.getSystemId() < 0 && cdma.getBaseStationId() < 0) {
            LogAlias.d("smarter", "cdma nid, sid, and bsid negative, invalid");
            valid = false;
            towerId = -1;
            return;
        }

        // Network 16 bit, system 15bit, basestation 16 bit
        towerId = ((long) cdma.getNetworkId() << 32) + ((long) cdma.getSystemId() << 16) + (long) cdma.getBaseStationId();

        if (towerId < 0) {
            LogAlias.d("smarter", "cdma tower problem:  valid tower nid " + cdma.getNetworkId() + " sid " + cdma.getSystemId() + " bsid " + cdma.getBaseStationId() + " but negative result, kluging to positive");
            towerId = Math.abs(towerId);
            valid = true;
            halfbad = true;
        }

    }

    public CellLocationCommon(CdmaCellLocation cdma) {
        setCdmaLocation(cdma);
    }

    public long getTowerId() {
        if (valid)
            return towerId;

        return -1;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isHalfbad() {
        return halfbad;
    }

    public LocationType getType() {
        return type;
    }

    public long getSeenTime() {
        return seenTime;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        if (!isValid())
            sb.append("INVALID ");
        if (isHalfbad())
            sb.append("HALFBAD ");
        sb.append(getType());
        sb.append(" ");
        sb.append(getTowerId());
        sb.append(" Seen ");
        sb.append(new Date(getSeenTime()).toString());
        sb.append("]");

        return sb.toString();
    }

    public boolean equals(CellLocationCommon c) {
        if (c == null)
            return false;

        return (c.getTowerId() == getTowerId());
    }

    public void setTowerEnabled(boolean enabled) {
        if (enabled)
            towerType = TowerType.TOWER_ENABLE;
    }

    public TowerType getTowerType() {
        return towerType;
    }
}
