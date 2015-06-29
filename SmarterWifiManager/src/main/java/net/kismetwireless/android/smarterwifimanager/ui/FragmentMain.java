package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;
import net.kismetwireless.android.smarterwifimanager.models.SmarterSSID;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/17/13.
 */
public class FragmentMain extends SmarterFragment {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Inject
    Bus eventBus;

    View mainView;

    ImageView mainImageView;

    TextView headlineText;

    SharedPreferences sharedPreferences;

    private SmarterWifiService.SmarterServiceCallback guiCallback = new SmarterWifiService.SmarterServiceCallback() {
        @Override
        public void wifiStateChanged(final SmarterSSID ssid, final SmarterWifiService.WifiState state,
                                     final SmarterWifiService.WifiState controlstate, final SmarterWifiService.ControlType type) {
            super.wifiStateChanged(ssid, state, controlstate, type);

            Activity ma = getActivity();

            if (ma == null)
                return;

            ma.runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    int iconResource = R.drawable.main_swm_idle;
                    int wifiTextResource = -1;
                    int reasonTextResource = -1;

                    wifiTextResource = SmarterWifiService.wifiStateToTextResource(state);
                    reasonTextResource = SmarterWifiService.controlTypeToTextResource(type, state);

                    if (state == SmarterWifiService.WifiState.WIFI_IDLE) {
                        iconResource = R.drawable.main_swm_idle;
                    } else if (state == SmarterWifiService.WifiState.WIFI_BLOCKED) {
                        iconResource = R.drawable.main_swm_disabled;
                    } else if (state == SmarterWifiService.WifiState.WIFI_IGNORE) {
                        iconResource = R.drawable.main_swm_ignore;
                    } else if (state == SmarterWifiService.WifiState.WIFI_OFF) {
                        if (type == SmarterWifiService.ControlType.CONTROL_BLUETOOTH)
                            iconResource = R.drawable.main_swm_bluetooth;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TIME)
                            iconResource = R.drawable.main_swm_time;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TOWER)
                            iconResource = R.drawable.main_swm_cell;
                        else
                            iconResource = R.drawable.main_swm_disabled;
                    } else if (state == SmarterWifiService.WifiState.WIFI_ON) {
                        if (type == SmarterWifiService.ControlType.CONTROL_BLUETOOTH)
                            iconResource = R.drawable.main_swm_bluetooth;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TIME)
                            iconResource = R.drawable.main_swm_time;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TOWER)
                            iconResource = R.drawable.main_swm_cell;
                        else
                            iconResource = R.drawable.main_swm_ignore;
                    }

                    mainImageView.setImageResource(iconResource);

                    headlineText.setText(serviceBinder.currentStateToComplexText());

                }
            });
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        eventBus.register(this);

        mainView = inflater.inflate(R.layout.fragment_main, container, false);

        mainImageView = (ImageView) mainView.findViewById(R.id.imageMain);
        headlineText = (TextView) mainView.findViewById(R.id.textViewHeadline);

        // Defer main setup until we've bound
        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            @Override
            public void run(SmarterWifiServiceBinder b) {
                if (!isAdded())
                    return;

                serviceBinder.addCallback(guiCallback);
            }
        });

        return mainView;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (serviceBinder != null)
            serviceBinder.removeCallback(guiCallback);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceBinder != null)
            serviceBinder.addCallback(guiCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        eventBus.unregister(this);
    }

    private boolean setManageWifi(boolean b) {
        if (serviceBinder == null)
            return false;

        SharedPreferences.Editor e = sharedPreferences.edit();
        e.putBoolean(getString(R.string.pref_enable), b);
        e.commit();

        eventBus.post(new EventPreferencesChanged());

        return true;
    }

    private boolean setLearnWifi(boolean b) {
        if (serviceBinder == null)
            return false;

        SharedPreferences.Editor e = sharedPreferences.edit();
        e.putBoolean(getString(R.string.pref_learn), b);
        e.commit();

        return true;
    }

    @Override
    public int getTitle() {
        return R.string.tab_main;
    }

    @Subscribe
    public void onEvent(EventPreferencesChanged evt) {
        if (sharedPreferences == null)
            return;

        Activity ma = getActivity();

        if (ma == null)
            return;

        /*
        ma.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sharedPreferences.getBoolean(getString(R.string.pref_enable), true)) {
                    switchManageWifi.setChecked(true);
                } else {
                    switchManageWifi.setChecked(false);
                }

                if (sharedPreferences.getBoolean(getString(R.string.pref_learn), true)) {
                    switchAutoLearn.setChecked(true);
                } else {
                    switchAutoLearn.setChecked(false);
                }
            }
        });
        */
    }
}
