package net.kismetwireless.android.smarterwifimanager.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
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
    public final static int REQUEST_LOCATION_PERMISSION = 1;

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
    // private View backgroundScanViewHolder, backgroundScanButton, backgroundScanHideButton;
    private View opennetworkViewHolder, opennetworkButton;
    private TextView opennetworkText;

    // private View backgroundScanViewMiniHolder, backgroundScanMiniButton;
    private View multiuserHolder;
    private View permissionViewHolder, permissonButton;

    private CompoundButton mainEnableToggle;

    private SharedPreferences sharedPreferences;

    private View learnedView, ignoreView, bluetoothView, timeView, settingsView;

    // Are we fully suppressed because we're a secondary user
    private boolean suppressedMultiuser = false;

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
                    LogAlias.d("smarter", "main activity, stage changed, " + state + " target " + controlstate);
                    boolean showSeparator = serviceBinder.getWifiAlwaysScanning();

                    int iconResource = R.drawable.main_swm_idle;

                    if (state == SmarterWifiService.WifiState.WIFI_IDLE) {
                        iconResource = R.drawable.main_swm_idle;

                        opennetworkViewHolder.setVisibility(View.GONE);

                        // We don't have a network but we think we should, offer to forget
                        if (type == SmarterWifiService.ControlType.CONTROL_TOWER) {
                            forgetViewHolder.setVisibility(View.VISIBLE);
                            showSeparator = true;
                        } else {
                            forgetViewHolder.setVisibility(View.GONE);
                        }

                        // If we're paused, allow unpausing
                        if (type == SmarterWifiService.ControlType.CONTROL_PAUSED) {
                            showSeparator = true;
                            pauseSwmHolder.setVisibility(View.VISIBLE);
                            ((TextView) pauseSwmButton).setText(R.string.main_resume_button);
                            pauseSwmButton.setOnClickListener(resumeClickListener);
                        } else {
                            pauseSwmHolder.setVisibility(View.GONE);
                        }

                    } else if (state == SmarterWifiService.WifiState.WIFI_BLOCKED) {
                        iconResource = R.drawable.main_swm_disabled;

                        forgetViewHolder.setVisibility(View.GONE);
                        opennetworkViewHolder.setVisibility(View.GONE);

                        // If wifi is blocked off, offer the option to add a network
                        pauseSwmHolder.setVisibility(View.VISIBLE);

                        showSeparator = true;

                    } else if (state == SmarterWifiService.WifiState.WIFI_IGNORE) {
                        iconResource = R.drawable.main_swm_ignore;

                        forgetViewHolder.setVisibility(View.GONE);
                        pauseSwmHolder.setVisibility(View.GONE);
                        opennetworkViewHolder.setVisibility(View.GONE);

                    } else if (state == SmarterWifiService.WifiState.WIFI_OFF) {
                        if (type == SmarterWifiService.ControlType.CONTROL_BLUETOOTH)
                            iconResource = R.drawable.main_swm_bluetooth;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TIME)
                            iconResource = R.drawable.main_swm_time;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TOWER || type == SmarterWifiService.ControlType.CONTROL_BGSCAN)
                            iconResource = R.drawable.main_swm_cell;
                        else
                            iconResource = R.drawable.main_swm_disabled;

                        forgetViewHolder.setVisibility(View.GONE);
                        opennetworkViewHolder.setVisibility(View.GONE);

                        // If wifi is off, show the option to pause and add, unless we're trying to turn it on
                        if (controlState != SmarterWifiService.WifiState.WIFI_ON) {
                            pauseSwmHolder.setVisibility(View.VISIBLE);
                            ((TextView) pauseSwmButton).setText(R.string.main_pause_button);
                            pauseSwmButton.setOnClickListener(pauseClickListener);

                            showSeparator = true;
                        } else {
                            pauseSwmHolder.setVisibility(View.GONE);
                        }

                    } else if (state == SmarterWifiService.WifiState.WIFI_ON) {
                        if (type == SmarterWifiService.ControlType.CONTROL_BLUETOOTH)
                            iconResource = R.drawable.main_swm_bluetooth;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TIME)
                            iconResource = R.drawable.main_swm_time;
                        else if (type == SmarterWifiService.ControlType.CONTROL_TOWER || type == SmarterWifiService.ControlType.CONTROL_BGSCAN)
                            iconResource = R.drawable.main_swm_cell;
                        else if (type == SmarterWifiService.ControlType.CONTROL_PAUSED)
                            iconResource = R.drawable.main_swm_add_waiting;
                        else
                            iconResource = R.drawable.main_swm_ignore;

                        forgetViewHolder.setVisibility(View.GONE);

                        opennetworkViewHolder.setVisibility(View.GONE);

                        // We're connected to something, so we don't need the ignore
                        pauseSwmHolder.setVisibility(View.GONE);
                    }

                    mainImageView.setImageResource(iconResource);

                    headlineText.setText(serviceBinder.currentStateToComplexText());

                }
            });
        }
    };

    private View.OnClickListener pauseClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ((TextView) v).setText(R.string.main_resume_button);
            v.setOnClickListener(resumeClickListener);

            serviceBinder.setPauseAddNewNetwork(true);

            // Launch wifi settings activity to connect to a new network
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
    };

    private View.OnClickListener resumeClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ((TextView) v).setText(R.string.main_pause_button);
            v.setOnClickListener(pauseClickListener);

            serviceBinder.setPauseAddNewNetwork(false);
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

        multiuserHolder = mainView.findViewById(R.id.layoutMainUserAlertHolder);

        permissionViewHolder = mainView.findViewById(R.id.layoutPermissionAlertHolder);
        permissonButton = mainView.findViewById(R.id.textViewPermissionButton);

        permissonButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissionUi();
            }
        });

        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            @Override
            public void run(SmarterWifiServiceBinder b) {
                // Shut down entirely if we're a secondary user
                if (b.getRunningAsSecondaryUser()) {
                    suppressedMultiuser = true;

                    Activity ma = getActivity();

                    if (ma == null)
                        return;

                    ma.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mainEnableToggle.setChecked(false);
                            mainEnableToggle.setClickable(false);
                            mainEnableToggle.setEnabled(false);
                            mainEnableToggle.setAlpha(0.5f);

                            mainEnableToggle.setOnCheckedChangeListener(null);
                            multiuserHolder.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    // If we need permissions, try to prompt right away
                    if (!b.getSufficientPermissions()) {
                        Activity ma = getActivity();

                        if (ma == null)
                            return;

                        ma.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                requestPermissionUi();
                            }
                        });
                    }
                }
            }
        });

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

        pauseSwmButton.setOnClickListener(pauseClickListener);

        forgetViewHolder = mainView.findViewById(R.id.layoutMainForgetHolder);
        forgetButton = mainView.findViewById(R.id.textViewMainForgetButton);

        forgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serviceBinder.doWifiDisable();
                serviceBinder.deleteCurrentTower();
                Snackbar.make(mainView, R.string.snackbar_delete_tower, Snackbar.LENGTH_LONG).show();
            }
        });

        /*
        backgroundScanViewHolder = mainView.findViewById(R.id.layoutMainBackgroundAlertHolder);
        backgroundScanButton = mainView.findViewById(R.id.textViewBackgroundAlertButton);
        backgroundScanHideButton = mainView.findViewById(R.id.textViewBackgroundIgnoreButton);

        backgroundScanViewMiniHolder = mainView.findViewById(R.id.layoutMainSmallBackgroundHolder);
        backgroundScanMiniButton = mainView.findViewById(R.id.textButtonBackgroundMore);
        */

        opennetworkViewHolder= mainView.findViewById(R.id.layoutMainOpenHolder);
        opennetworkButton = mainView.findViewById(R.id.textViewOpenAlertButton);
        opennetworkText = (TextView) mainView.findViewById(R.id.textViewOpenAlert);

        if (sharedPreferences.getBoolean(getString(R.string.prefs_item_ignore_open), false)) {
            opennetworkText.setText(R.string.main_open_nontracking_text);
        }

        // Open the advanced settings
        /*
        backgroundScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        intent.setClassName("com.android.settings", "com.android.settings.Settings$LocationSettingsActivity");
                        startActivity(intent);
                        return;
                    } catch (ActivityNotFoundException e) {
                        ;
                    }
                } else {
                    // Try to run the default advanced
                    try {
                        intent.setClassName("com.android.settings", "com.android.settings.Settings$AdvancedWifiSettingsActivity");
                        startActivity(intent);
                        return;
                    } catch (ActivityNotFoundException e) {
                        ;
                    }
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

        backgroundScanHideButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor e = sharedPreferences.edit();
                e.putBoolean("MAIN_HIDE_BG_SCAN", true);
                e.commit();

                backgroundScanViewMiniHolder.setVisibility(View.VISIBLE);
                backgroundScanViewHolder.setVisibility(View.GONE);
            }
        });

        backgroundScanMiniButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor e = sharedPreferences.edit();
                e.putBoolean("MAIN_HIDE_BG_SCAN", false);
                e.commit();

                backgroundScanViewMiniHolder.setVisibility(View.GONE);
                backgroundScanViewHolder.setVisibility(View.VISIBLE);
            }
        });
        */

        learnedView = mainView.findViewById(R.id.layoutMainNavLearned);
        ignoreView = mainView.findViewById(R.id.layoutMainNavIgnored);
        bluetoothView = mainView.findViewById(R.id.layoutMainNavBluetooth);
        timeView = mainView.findViewById(R.id.layoutMainNavTime);
        settingsView = mainView.findViewById(R.id.layoutMainNavSettings);

        learnedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ActivitySsidLearned.class));
            }
        });

        ignoreView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ActivitySsidBlacklist.class));
            }
        });

        bluetoothView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ActivityBluetoothBlacklist.class));

            }
        });

        timeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ActivityTimeRange.class));
            }
        });

        settingsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), ActivityPrefs.class));
            }
        });

        // Defer main setup until we've bound
        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            @Override
            public void run(SmarterWifiServiceBinder b) {
                if (!isAdded())
                    return;

                serviceBinder.addCallback(guiCallback);

                /*
                if (serviceBinder.getWifiAlwaysScanning()) {
                    if (sharedPreferences.getBoolean("MAIN_HIDE_BG_SCAN", false)) {
                        backgroundScanViewMiniHolder.setVisibility(View.VISIBLE);
                        backgroundScanViewHolder.setVisibility(View.GONE);
                    } else {
                        backgroundScanViewHolder.setVisibility(View.VISIBLE);
                        backgroundScanViewMiniHolder.setVisibility(View.GONE);
                    }
                } else {
                    backgroundScanViewHolder.setVisibility(View.GONE);
                    backgroundScanViewMiniHolder.setVisibility(View.GONE);
                }
                */

                updatePermissionUi();
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

        // Do nothing if we're suppressed
        if (suppressedMultiuser)
            return;

        if (serviceBinder != null) {
            serviceBinder.addCallback(guiCallback);

            /*
            if (backgroundScanViewHolder != null) {
                if (serviceBinder.getWifiAlwaysScanning()) {
                    if (sharedPreferences.getBoolean("MAIN_HIDE_BG_SCAN", false)) {
                        backgroundScanViewMiniHolder.setVisibility(View.VISIBLE);
                    } else {
                        backgroundScanViewHolder.setVisibility(View.VISIBLE);
                    }
                } else {
                    backgroundScanViewHolder.setVisibility(View.GONE);
                    backgroundScanViewMiniHolder.setVisibility(View.GONE);
                }
            }
            */

            if (sharedPreferences.getBoolean(getString(R.string.prefs_item_ignore_open), false)) {
                opennetworkText.setText(R.string.main_open_nontracking_text);
            }

            updatePermissionUi();
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

    private void updatePermissionUi() {
        // Do nothing if we're secondary user
        if (suppressedMultiuser)
            return;

        // Do nothing if we can't reach the service, we'll get picked up when the bind completes
        if (serviceBinder == null)
            return;

        if (!serviceBinder.getSufficientPermissions()) {
            LogAlias.d("smarter", "fragmentmain, insufficient permissions, disabling toggle");

            Activity ma = getActivity();

            if (ma == null)
                return;

            mainEnableToggle.setClickable(false);

            mainEnableToggle.setEnabled(false);
            mainEnableToggle.setAlpha(0.5f);

            permissionViewHolder.setVisibility(View.VISIBLE);
        } else {
            // Restore functionality if we get permission while we're gone
            LogAlias.d("smarter", "fragmentmain, sufficient permissions now");
            mainEnableToggle.setClickable(true);
            mainEnableToggle.setEnabled(true);
            mainEnableToggle.setAlpha(1.0f);
            permissionViewHolder.setVisibility(View.GONE);

            if (sharedPreferences.getBoolean(getString(R.string.pref_enable), true)) {
                mainEnableToggle.setChecked(true);
            } else {
                mainEnableToggle.setChecked(false);
            }
        }
    }

    private void requestPermissionUi() {
        final Activity activity = getActivity();

        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                builder.setTitle(R.string.permission_dialog_title);
                builder.setMessage(R.string.permission_dialog_location);

                builder.setNegativeButton(R.string.dialog_button_permission_skip,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Do nothing
                    }
                });

                builder.setPositiveButton(R.string.dialog_button_permission, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(activity,
                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                REQUEST_LOCATION_PERMISSION);
                    }
                });

                builder.create().show();
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_LOCATION_PERMISSION);
            }
        }
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
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (serviceBinder != null) {
                        serviceBinder.getSufficientPermissions();
                        serviceBinder.configureWifiState();

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updatePermissionUi();
                            }
                        });
                    }
                } else {
                    // Do nothing, we still don't have permissions.
                }
                return;
            }
        }
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

                if (sharedPreferences.getBoolean(getString(R.string.prefs_item_ignore_open), false)) {
                    opennetworkText.setText(R.string.main_open_nontracking_text);
                } else {
                    opennetworkText.setText(R.string.main_open_text);
                }
            }
        });
    }
}
