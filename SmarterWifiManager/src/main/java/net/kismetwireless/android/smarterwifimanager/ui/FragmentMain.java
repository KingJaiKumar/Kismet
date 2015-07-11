package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
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

    private View mainView;

    private ImageView mainImageView;
    private TextView headlineText;

    private View pauseSwmHolder, pauseSwmButton;
    private View forgetViewHolder, forgetButton;
    private View backgroundscanViewHolder, backgroundScanButton;
    private View opennetworkViewHolder, opennetworkButton;

    private CompoundButton mainEnableToggle;

    private SharedPreferences sharedPreferences;

    View advancedWifiSettingsView;

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

                    if (state == SmarterWifiService.WifiState.WIFI_IDLE) {
                        iconResource = R.drawable.main_swm_idle;
                    } else if (state == SmarterWifiService.WifiState.WIFI_BLOCKED) {
                        iconResource = R.drawable.main_swm_disabled;

                        forgetViewHolder.setVisibility(View.GONE);
                        opennetworkViewHolder.setVisibility(View.GONE);

                        pauseSwmHolder.setVisibility(View.VISIBLE);

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

        mainEnableToggle = (CompoundButton) mainView.findViewById(R.id.switchSwmEnable);

        if (sharedPreferences.getBoolean(getString(R.string.pref_enable), true)) {
            mainEnableToggle.setChecked(true);
        } else {
            mainEnableToggle.setChecked(false);
        }

        mainEnableToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                setManageWifi(b);
            }
        });

        pauseSwmHolder = mainView.findViewById(R.id.layoutMainPauseHolder);
        pauseSwmButton = mainView.findViewById(R.id.textViewPauseButton);

        forgetViewHolder = mainView.findViewById(R.id.layoutMainForgetHolder);
        forgetButton = mainView.findViewById(R.id.textViewMainForgetButton);

        backgroundscanViewHolder = mainView.findViewById(R.id.layoutMainBackgroundAlertHolder);
        backgroundScanButton = mainView.findViewById(R.id.textViewBackgroundAlertButton);

        opennetworkViewHolder= mainView.findViewById(R.id.layoutMainOpenHolder);
        opennetworkButton = mainView.findViewById(R.id.textViewOpenAlertButton);

        // Defer main setup until we've bound
        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            @Override
            public void run(SmarterWifiServiceBinder b) {
                if (!isAdded())
                    return;

                serviceBinder.addCallback(guiCallback);
            }
        });


        // Open the advanced settings
        backgroundScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                // Try to run the default advanced
                try {
                    intent.setClassName("com.android.settings", "com.android.settings.Settings$AdvancedWifiSettingsActivity");
                    startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    ;
                }

                // Try LG's BS alternate
                try {
                    intent.setClassName("com.lge.wifisettings", "com.lge.wifisettings.activity.AdvancedWifiSettingsActivity");
                    startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    ;
                }

                // Go to the standard wifi settings and the user has to get to advanced themselves, sorry
                Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
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

        if (serviceBinder != null) {
            serviceBinder.addCallback(guiCallback);

            if (backgroundscanViewHolder != null) {
                if (serviceBinder.getWifiAlwaysScanning()) {
                    backgroundscanViewHolder.setVisibility(View.VISIBLE);
                } else {
                    backgroundscanViewHolder.setVisibility(View.GONE);
                }
            }
        }

        if (sharedPreferences.getBoolean(getString(R.string.pref_enable), true)) {
            mainEnableToggle.setChecked(true);
        } else {
            mainEnableToggle.setChecked(false);
        }
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

        ma.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sharedPreferences.getBoolean(getString(R.string.pref_enable), true)) {
                    mainEnableToggle.setChecked(true);
                } else {
                    mainEnableToggle.setChecked(false);
                }
            }
        });
    }
}
