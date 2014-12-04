package net.kismetwireless.android.smarterwifimanager.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.preference.PreferenceManager;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiConnected;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiDisabled;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiEnabled;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/2/13.
 */
public class NetworkReceiver extends BroadcastReceiver {
    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Inject
    Bus eventBus;

    @Override
    public void onReceive(Context context, Intent intent) {
        SmarterApplication.get(context).inject(this);

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

        if (!p.getBoolean("start_boot", true)) {
            if (!SmarterWifiServiceBinder.isServiceRunning(context)) {
                LogAlias.d("smarter", "Would have done something but service isn't running and we're not autostarting");
                return;
            }
        }

        try {
            // Collapse upping/up and downing/down status into single events
            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                int oldWifiState = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

                if ((oldWifiState == WifiManager.WIFI_STATE_DISABLED || oldWifiState == WifiManager.WIFI_STATE_DISABLING) &&
                        (wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_ENABLING)) {
                    eventBus.post(new EventWifiEnabled());
                } else if ((wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING) &&
                        (oldWifiState == WifiManager.WIFI_STATE_ENABLED || oldWifiState == WifiManager.WIFI_STATE_ENABLING)) {
                    eventBus.post(new EventWifiDisabled());
                }
            } else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (ni.getType() == ConnectivityManager.TYPE_WIFI && ni.isConnected()) {
                    eventBus.post(new EventWifiConnected());
                }
            }

            // TODO - remove this once we finish converting to eventbus
            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
                    public void run(SmarterWifiServiceBinder b) {
                        b.configureWifiState();
                    }
                });
            }

            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (ni.getType() != ConnectivityManager.TYPE_WIFI)
                    return;

                serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
                    public void run(SmarterWifiServiceBinder b) {
                        b.configureWifiState();
                    }
                });

            }
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
                    public void run(SmarterWifiServiceBinder b) {
                        b.configureBluetoothState();
                    }
                });
            }

            if (intent.getAction().equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                final BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);

                serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
                    public void run(SmarterWifiServiceBinder b) {
                        b.handleBluetoothDeviceState(bluetoothDevice, state);
                    }
                });

                // LogAlias.d("smarter", "bcast rx got bt device " + bluetoothDevice.getAddress() + " " + bluetoothDevice.getName() + " state " + state);
            }

            LogAlias.d("smarter", "bcast rx: " + intent.getAction());

            if (intent.getAction().equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                final int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

                LogAlias.d("smarter", "got wifi p2p state " + state);

                if (state != -1) {
                    serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
                        public void run(SmarterWifiServiceBinder b) {
                            b.handleWifiP2PState(state);
                        }
                    });
                }

            }

        } catch (NullPointerException npe) {
            // Don't care.  Sometimes the service fails and this happens, and I don't know why.
        }

    }
}
