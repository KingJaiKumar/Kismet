package net.kismetwireless.android.smarterwifimanager.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/24/13.
 */
public class FragmentPrefs extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    ListPreference timeoutPref;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SmarterApplication.get(getActivity()).inject(this);

        addPreferencesFromResource(R.xml.main_prefs);

        timeoutPref = (ListPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_item_shutdowntime));

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        setPrefsSummary();
    }

    @Override
    public void onResume() {
        super.onResume();

        setPrefsSummary();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        setPrefsSummary();
    }

    private void setPrefsSummary() {
        CheckBoxPreference wifiAggressivePref = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_item_aggressive_wifi_background));
        CheckBoxPreference wifiBgPref = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_item_use_background));
        CheckBoxPreference aggressiveTowerPref = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(R.string.prefs_item_aggressive));

        if (!serviceBinder.getWifiBgScanCapable()) {
            // Disable it all if we can't support it on this android at all
            wifiBgPref.setEnabled(false);
            wifiAggressivePref.setEnabled(false);
            wifiBgPref.setSummary(getString(R.string.prefs_item_use_background_unavailable));
            wifiAggressivePref.setSummary(getString(R.string.prefs_item_use_background_unavailable));
            wifiBgPref.setChecked(false);
        } else {
            if (!serviceBinder.getWifiAlwaysScanning()) {
                // If bg scanning is turned off, disable the options and set the summaries but leave
                // the check state
                wifiBgPref.setEnabled(false);
                wifiAggressivePref.setEnabled(false);
                wifiBgPref.setSummary(getString(R.string.prefs_item_use_background_off));
                wifiAggressivePref.setSummary(getString(R.string.prefs_item_use_background_off));

                wifiBgPref.setEnabled(false);
                wifiAggressivePref.setEnabled(false);
                aggressiveTowerPref.setEnabled(true);
            } else {
                // Restore the descriptions, enable them, don't touch the check state
                wifiBgPref.setEnabled(true);
                wifiAggressivePref.setEnabled(true);
                wifiBgPref.setSummary(getString(R.string.prefs_item_use_background_explanation));
                wifiAggressivePref.setSummary(getString(R.string.prefs_item_aggressive_wifi_background_explanation));

                if (wifiBgPref.isChecked()) {
                    aggressiveTowerPref.setEnabled(false);
                    wifiAggressivePref.setEnabled(true);
                } else {
                    aggressiveTowerPref.setEnabled(true);
                    wifiAggressivePref.setEnabled(false);
                }
            }
        }

        CharSequence pt = timeoutPref.getEntry();

        String s = "";
        if (pt != null)
            s = pt.toString() + "\n";

        timeoutPref.setSummary(s + getString(R.string.prefs_item_shutdowntime_explanation));
    }
}
