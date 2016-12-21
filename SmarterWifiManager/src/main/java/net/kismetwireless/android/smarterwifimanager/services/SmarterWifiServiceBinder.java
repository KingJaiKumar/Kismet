package net.kismetwireless.android.smarterwifimanager.services;

import android.app.ActivityManager;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.models.SmarterBluetooth;
import net.kismetwireless.android.smarterwifimanager.models.SmarterSSID;
import net.kismetwireless.android.smarterwifimanager.models.SmarterTimeRange;

import java.util.ArrayList;

public class SmarterWifiServiceBinder {
    private SmarterWifiService smarterService;
    private boolean isBound;
    Context context;

    ArrayList<BinderCallback> binderList = new ArrayList<>();

    ArrayList<SmarterWifiService.SmarterServiceCallback> pendingList = new ArrayList<SmarterWifiService.SmarterServiceCallback>();
    ArrayList<SmarterWifiService.SmarterServiceCallback> registeredList = new ArrayList<SmarterWifiService.SmarterServiceCallback>();

    public static class BinderCallback {
        public void run(SmarterWifiServiceBinder binder) {
            return;
        }
    }

    public static boolean isServiceRunning(Context c) {
        ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SmarterWifiService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LogAlias.d("smarter", "binder serviceConnection onserviceconnected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SmarterWifiService.ServiceBinder binder = (SmarterWifiService.ServiceBinder) service;
            smarterService = binder.getService();

            isBound = true;

            synchronized (this) {
                if (binderList.size() > 0) {
                    for (BinderCallback b : binderList) {
                        b.run(SmarterWifiServiceBinder.this);
                    }
                }
            }

            synchronized (this) {
                if (pendingList.size() > 0) {
                    for (SmarterWifiService.SmarterServiceCallback cb : pendingList) {
                        smarterService.addCallback(cb);
                        registeredList.add(cb);
                    }
                }

                pendingList.clear();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    public SmarterWifiServiceBinder(Context c) {
        LogAlias.d("smarter", "service binder new()");
        context = c;
    }

    public boolean getIsBound() {
        return isBound;
    }

    public SmarterWifiService getService() {
        return smarterService;
    }

    void doKillService() {
        if (smarterService == null)
            return;

        if (!isBound)
            return;

        smarterService.shutdownService();
    }

    // Call a cb as soon as we finish binding
    public void doCallAndBindService(BinderCallback cb) {
        Log.d("smarter", "service binder call and bind, isbound=" + isBound);

        if (isBound) {
            Log.d("smarter", "service binder already bound, not rebinding");
            cb.run(this);
            return;
        }

        if (cb != null) {
            synchronized (this) {
                binderList.add(cb);
            }
        }

        doBindService();
    }

    public void doStartService() {
        Intent svc = new Intent(context.getApplicationContext(), SmarterWifiService.class);
        context.getApplicationContext().startService(svc);
    }

    void doBindServiceWithoutStart() {
        if (isBound)
            return;
    }

    public void doBindService() {
        if (isBound)
            return;

        // Might as well always try to start
        doStartService();

        // We want to bind in the application context
        context.getApplicationContext().bindService(new Intent(context, SmarterWifiService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
    }


    public void doUnbindService() {
        if (isBound) {
            if (smarterService != null) {
                for (SmarterWifiService.SmarterServiceCallback cb : registeredList)
                        smarterService.removeCallback(cb);

                // If we can't unbind just silently ignore it
                try {
                    context.unbindService(serviceConnection);
                } catch (IllegalArgumentException e) {

                }
            }
        }

        smarterService = null;
        isBound = false;
    }

    public void configureWifiState() {
        if (smarterService == null) {
            Log.e("smarter", "service null configurewifistate");
            return;
        }

        smarterService.configureWifiState();
    }

    public void configureTimerangeState() {
        if (smarterService == null) {
            Log.e("smarter", "service null configuretimerangestate");
            return;
        }

        smarterService.configureTimerangeState();
    }

    public ArrayList<SmarterBluetooth> getBluetoothBlacklist() {
        if (smarterService == null) {
            Log.e("smarter", "service null getting bt blacklist");
            return null;
        }

        return smarterService.getBluetoothBlacklist();
    }

    public void setBluetoothBlacklisted(SmarterBluetooth bt, boolean blacklist, boolean enable) {
        if (smarterService == null) {
            Log.e("smarter", "service null settting bt blacklist");
            return;
        }

        smarterService.setBluetoothBlacklist(bt, blacklist, enable);
    }

    public ArrayList<SmarterSSID> getSsidBlacklist() {
        if (smarterService == null) {
            Log.e("smarter", "service null getting blacklist");
            return null;
        }

        return smarterService.getSsidBlacklist();
    }

    public void setSsidBlacklisted(SmarterSSID e, boolean b) {
        if (smarterService == null) {
            Log.e("smarter", "Service null setting blacklisted ssid");
            return;
        }

        smarterService.setSsidBlacklist(e, b);
    }

    public ArrayList<SmarterSSID> getSsidLearnedlist() {
        if (smarterService == null) {
            Log.e("smarter", "service null getting towerlist");
            return null;
        }

        return smarterService.getSsidLearnedlist();
    }

    public void deleteSsidTowerMap(SmarterSSID ssid) {
        if (smarterService == null) {
            Log.e("smarter", "service null deleting towermap");
            return;
        }

        smarterService.deleteSsidTowerMap(ssid);
    }

    public void deleteBssidMap(SmarterSSID ssid) {
        if (smarterService == null) {
            Log.e("smarter", "service null deleting towermap");
            return;
        }

        smarterService.deleteBssidMap(ssid);
    }

    public void deleteCurrentTower() {
        if (smarterService == null) {
            Log.e("smarter", "service null deleting current tower");
            return;
        }

        smarterService.deleteCurrentTower();
    }

    public long getLastTowerMap() {
        if (smarterService == null) {
            Log.e("smarter", "service null getting last towermap");
            return -1;
        }

        return smarterService.getLastTowerMap();
    }

    public ArrayList<SmarterTimeRange> getTimeRangeList() {
        if (smarterService == null) {
            Log.e("smarter", "service null getting timeranges");
            return null;
        }

        return smarterService.getTimeRangeList();
    }

    public void deleteTimeRange(SmarterTimeRange r) {
        if (smarterService == null) {
            Log.e("smarter", "service null deleting timerange");
            return;
        }

        smarterService.deleteTimeRange(r);
    }

    public long updateTimeRange(SmarterTimeRange r) {
        if (smarterService == null) {
            Log.e("smarter", "service null updating timerange");
            return -1;
        }

        return smarterService.updateTimeRange(r);
    }

    public long updateTimeRangeEnabled(SmarterTimeRange r) {
        if (smarterService == null) {
            Log.e("smarter", "service null updating timerangeenabled");
            return -1;
        }

        return smarterService.updateTimeRangeEnabled(r);
    }

    public void addCallback(SmarterWifiService.SmarterServiceCallback cb) {
        synchronized (this) {
            if (smarterService == null) {
                pendingList.add(cb);
            } else {
                smarterService.addCallback(cb);
            }
        }
    }

    public void removeCallback(SmarterWifiService.SmarterServiceCallback cb) {
        synchronized (this) {
            if (smarterService == null) {
                pendingList.remove(cb);
            } else {
                smarterService.removeCallback(cb);
                registeredList.remove(cb);
            }
        }
    }

    public void configureBluetoothState() {
        if (smarterService == null) {
            Log.e("smarter", "configure bt state while service null");
            return;
        }

        smarterService.configureBluetoothState();
    }

    public void handleBluetoothDeviceState(BluetoothDevice d, int state) {
        if (smarterService == null) {
            Log.e("smarter", "btdevicestate while service null");
            return;
        }

        smarterService.handleBluetoothDeviceState(d, state);
    }

    public void handleWifiP2PState(int state) {
        if (smarterService == null) {
            Log.e("smarter", "wifip2p while service null");
            return;
        }

        smarterService.handleWifiP2PState(state);

    }

    public String currentStateToComplexText() {
        if (smarterService == null) {
            Log.e("smarter", "currentStateToComplexText while service null");
            return "Can't get state, service is null in binder";
        }

        return smarterService.currentStateToComplexText();
    }

    public void handleWifiScan() {
        if (smarterService == null) {
            return;
        }

        smarterService.handleWifiScan();
    }

    public boolean getWifiBgScanCapable() {
        if (smarterService == null) {
            return false;
        }

        return smarterService.getWifiBgScanCapable();
    }

    public boolean getWifiAlwaysScanning() {
        if (smarterService == null) {
            Log.e("smarter", "getWifiAlwaysScanning while service null");
            return false;
        }

        return smarterService.getWifiAlwaysScanning();
    }

    public void setPauseAddNewNetwork(boolean v) {
        if (smarterService == null) {
            Log.e("smarter", "setPauseAddNewNetwork while service null");
            return;
        }

        smarterService.setPauseAddNewNetwork(v);
    }

    public boolean getPauseAddNewNetwork() {
        if (smarterService == null) {
            Log.e("smarter", "getPauseAddNewNetwork while service null");
            return false;
        }

        return smarterService.getPauseAddNewNetwork();
    }

    public void doWifiDisable() {
        if (smarterService == null) {
            Log.e("smarter", "doWifiDisabled while service null");
            return;
        }

        smarterService.doWifiDisable();
    }

    public boolean getRunningAsSecondaryUser() {
        if (smarterService == null) {
            Log.e("smarter", "getRunningAsSecondaryUser while service null");
            return false;
        }

        return smarterService.getRunningAsSecondaryUser();
    }

    public boolean getSufficientPermissions() {
        if (smarterService == null) {
            Log.e("smarter", "getSufficientPermissions while service null");
            return false;
        }

        return smarterService.checkForPermissions();
    }

    public void pingOnWakeup() {
        if (smarterService == null) {
            Log.e("smarter", "pingOnWakeup while service null");
            return;
        }

        smarterService.pingOnWakeup();
    }
}