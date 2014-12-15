package net.kismetwireless.android.smarterwifimanager.services;

import android.content.Context;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventCellTower;

import java.util.List;

import javax.inject.Inject;

/**
 * Created by dragorn on 12/14/14.
 */
public class SmarterPhoneStateListener extends PhoneStateListener {
    @Inject
    Bus eventBus;

    TelephonyManager telephonyManager;

    public SmarterPhoneStateListener(Context c) {
        SmarterApplication.get(c).inject(this);

        telephonyManager = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public void onCellLocationChanged(CellLocation location) {
        LogAlias.d("smarter", "onCellLocationChanged, posting new location");
        eventBus.post(new EventCellTower(location));
    }

    @Override
    public void onCellInfoChanged(List<CellInfo> infos) {
        // Shim the info list changing since we don't know how to really deal with that data; get the current location
        LogAlias.d("smarter", "onCellInfoChanged, something about our cell or neighboring cells changed; posting current cell only");
        eventBus.post(new EventCellTower(telephonyManager.getCellLocation()));
    }
}
