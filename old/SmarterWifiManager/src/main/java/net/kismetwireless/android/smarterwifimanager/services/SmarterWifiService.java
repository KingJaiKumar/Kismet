package net.kismetwireless.android.smarterwifimanager.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.squareup.otto.Bus;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventCellTower;
import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiConnected;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiDisconnected;
import net.kismetwireless.android.smarterwifimanager.events.EventWifiState;
import net.kismetwireless.android.smarterwifimanager.models.CellLocationCommon;
import net.kismetwireless.android.smarterwifimanager.models.SmarterBluetooth;
import net.kismetwireless.android.smarterwifimanager.models.SmarterDBSource;
import net.kismetwireless.android.smarterwifimanager.models.SmarterSSID;
import net.kismetwireless.android.smarterwifimanager.models.SmarterTimeRange;
import net.kismetwireless.android.smarterwifimanager.models.SmarterWorldState;
import net.kismetwireless.android.smarterwifimanager.ui.MainActivity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

public class SmarterWifiService extends Service {
    // Unique combinations:
    // WIFISTATE IGNORE + CONTROL RANGE - no valid towers no other conditions

    public enum ControlType {
        CONTROL_DISABLED, CONTROL_USER, CONTROL_TOWER, CONTROL_TOWERID, CONTROL_BGSCAN, CONTROL_GEOFENCE,
        CONTROL_BLUETOOTH, CONTROL_TIME, CONTROL_SSIDBLACKLIST, CONTROL_AIRPLANE, CONTROL_TETHER,
        CONTROL_SLEEPPOLICY, CONTROL_PAUSED, CONTROL_NEVERRUN, CONTROL_PERMISSIONS, CONTROL_OPEN,
    }

    public enum WifiState {
        // Hard blocked, on, off, idle, ignore
        WIFI_BLOCKED, WIFI_ON, WIFI_OFF, WIFI_IDLE, WIFI_IGNORE
    }

    public enum BluetoothState {
        BLUETOOTH_BLOCKED, BLUETOOTH_ON, BLUETOOTH_OFF, BLUETOOTH_IDLE, BLUETOOTH_IGNORE
    }

    private boolean sufficientPermissions = false;
    private boolean everBeenRun = false;
    private boolean shutdown = false;

    private SharedPreferences preferences;

    private SmarterPhoneStateListener phoneListener;

    private TelephonyManager telephonyManager;
    private WifiManager wifiManager;
    private BluetoothAdapter btAdapter;
    private ConnectivityManager connectivityManager;
    private NotificationManager notificationManager;

    private boolean proctorWifi = true;
    private boolean learnWifi = true;

    private int enableWaitSeconds = 1;
    private int disableWaitSeconds = 60;
    private boolean showNotification = true;
    private boolean performTowerPurges = false;
    private boolean aggressiveTowerCheck = false;
    private int purgeTowerHours = 168;
    private boolean ignoreOpenNetworks = false;
    private boolean useWifiScan = false;
    private boolean aggressiveWifiScan = false;

    private WifiState userOverrideState = WifiState.WIFI_IGNORE;
    private WifiState curState = WifiState.WIFI_IGNORE;
    private WifiState targetState = WifiState.WIFI_IGNORE;
    private WifiState previousState = WifiState.WIFI_IGNORE;

    private ControlType lastControlReason = ControlType.CONTROL_TOWERID;

    private Handler timerHandler = new Handler();

    private NotificationCompat.Builder notificationBuilder;

    private long lastTowerMap = 0;

    // Are we paused waiting to connect to a new network?
    private boolean pausedNewNetwork = false;

    private boolean bluetoothEnabled = false;
    private boolean bluetoothBlocking = false;
    private boolean initialBluetoothState = false;
    private HashMap<String, SmarterBluetooth> bluetoothBlockingDevices = new HashMap<String, SmarterBluetooth>();
    private HashMap<String, SmarterBluetooth> bluetoothConnectedDevices = new HashMap<String, SmarterBluetooth>();

    private SmarterTimeRange currentTimeRange, nextTimeRange;

    private AlarmReceiver alarmReceiver;
    private AlarmManager alarmManager;
    private PendingIntent wifiDownIntent;
    private PendingIntent wifiUpIntent;

    private PendingIntent towerCheckIntent;

    private boolean pendingWifiShutdown = false, pendingBluetoothShutdown = false;

    public static abstract class SmarterServiceCallback {
        protected ControlType controlType;
        protected WifiState wifiState, controlState;
        protected SmarterSSID lastSsid;
        protected CellLocationCommon.TowerType towerType;
        protected BluetoothState lastBtState;

        public void wifiStateChanged(final SmarterSSID ssid, final WifiState state,
                                     final WifiState controlstate, final ControlType type) {
            lastSsid = ssid;
            wifiState = state;
            controlType = type;
            controlState = controlstate;

            return;
        }

        public void towerStateChanged(final long towerid, final CellLocationCommon.TowerType type) {
            towerType = type;

            return;
        }

        public void bluetoothStateChanged(final BluetoothState state) {
            lastBtState = state;

            return;
        }
    }

    ArrayList<SmarterServiceCallback> callbackList = new ArrayList<SmarterServiceCallback>();

    // Minimal in the extreme binder which returns the service for direct calling
    public class ServiceBinder extends Binder {
        SmarterWifiService getService() {
            return SmarterWifiService.this;
        }
    }

    private ServiceBinder serviceBinder = new ServiceBinder();

    private NetworkReceiver networkReceiver;

    @Inject
    Context context;

    @Inject
    Bus eventBus;

    @Inject
    SmarterDBSource dbSource;

    @Inject
    SmarterWorldState worldState;

    @Override
    public void onCreate() {
        super.onCreate();

        SmarterApplication.get(this).inject(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // Default network state
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        phoneListener = new SmarterPhoneStateListener(this);

        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        btAdapter = BluetoothAdapter.getDefaultAdapter();

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(context);

        // Check permissions
        if (checkForPermissions()) {
            configurePermissionListeners();
        }

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmReceiver = new AlarmReceiver();

        alarmReceiver.setAlarm(context, System.currentTimeMillis() + (20 * 1000));

        // Make the notification
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher_notification_idle);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        notificationBuilder.setOnlyAlertOnce(true);
        notificationBuilder.setOngoing(true);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        notificationBuilder.setContentIntent(pIntent);

        // Get the initial BT enable state
        initialBluetoothState = getBluetoothState() != BluetoothState.BLUETOOTH_OFF;

        onEvent(new EventPreferencesChanged());

        // Register the event bus
        eventBus.register(this);

        // Update the time range database which also fires BT and Wifi configurations
        configureTimerangeState();

        // Set the initial wifi info
        SmarterSSID ssid = getCurrentSsid();
        if (ssid != null && !ssid.isBlacklisted() && (ssid.isEncrypted() || !ignoreOpenNetworks)) {
            LogAlias.d("smarter", "Logging startup BSSID " + ssid.getBssid() + " for " + ssid.getSsid());
            dbSource.mapBssid(ssid);
        }

        if (getWifiBgScanCapable()) {
            LogAlias.d("smarter", "Starting initial background scan");
            handleWifiScan();
            wifiManager.startScan();
        }

        if (showNotification)
            notificationManager.notify(0, notificationBuilder.build());

        addCallback(new SmarterServiceCallback() {
            WifiState lastState = WifiState.WIFI_IDLE;
            ControlType lastControl = ControlType.CONTROL_DISABLED;

            @Override
            public void wifiStateChanged(SmarterSSID ssid, WifiState state, WifiState controlstate, ControlType type) {
                super.wifiStateChanged(ssid, state, controlstate, type);

                if (state == lastState && type == lastControl)
                    return;

                lastState = state;
                lastControl = type;

                int wifiIconId = R.drawable.ic_launcher_notification_ignore;
                int wifiTextResource = -1;
                int reasonTextResource = -1;

                if (getRunningAsSecondaryUser()) {
                    wifiIconId = R.drawable.ic_launcher_notification_idle;

                    wifiTextResource = R.string.notification_title_ignore;
                    reasonTextResource = R.string.notification_multiuser;

                } else if (state == WifiState.WIFI_IDLE) {
                    wifiIconId = R.drawable.ic_launcher_notification_idle;

                    wifiTextResource = R.string.notification_title_on;
                    reasonTextResource = R.string.notification_idle;

                } else if (state == WifiState.WIFI_BLOCKED) {
                    wifiIconId = R.drawable.ic_launcher_notification_disabled;

                    wifiTextResource = R.string.notification_title_off;

                    if (type == ControlType.CONTROL_BLUETOOTH) {
                        wifiIconId = R.drawable.ic_launcher_notification_bluetooth;
                        reasonTextResource = R.string.notification_wifi_bt;
                    } else if (type == ControlType.CONTROL_TIME) {
                        wifiIconId = R.drawable.ic_launcher_notification_clock;
                        reasonTextResource = R.string.notification_wifi_time;
                    }
                } else if (state == WifiState.WIFI_IGNORE) {
                    wifiIconId = R.drawable.ic_launcher_notification_idle;

                    wifiTextResource = R.string.notification_title_ignore;

                    if (type == ControlType.CONTROL_AIRPLANE) {
                        reasonTextResource = R.string.notification_airplane;
                    } else if (type == ControlType.CONTROL_SSIDBLACKLIST) {
                        reasonTextResource = R.string.notification_ignoring;
                    } else if (type == ControlType.CONTROL_DISABLED) {
                        reasonTextResource = R.string.notification_swm_disabled;
                    } else if (type == ControlType.CONTROL_NEVERRUN) {
                        reasonTextResource = R.string.notification_wifi_neverrun;
                    } else if (type == ControlType.CONTROL_OPEN) {
                        reasonTextResource = R.string.notification_openwifi;
                    } else if (type == ControlType.CONTROL_TETHER) {
                        reasonTextResource = R.string.notification_tethered;
                    }

                } else if (state == WifiState.WIFI_OFF) {
                    wifiTextResource = R.string.notification_title_off;

                    if (controlState == WifiState.WIFI_ON) {
                        wifiIconId = R.drawable.ic_launcher_notification_cell;
                        reasonTextResource = R.string.notification_bringup;
                    } else if (type == ControlType.CONTROL_BLUETOOTH) {
                        wifiIconId = R.drawable.ic_launcher_notification_bluetooth;
                        reasonTextResource = R.string.notification_wifi_bt;
                    } else if (type == ControlType.CONTROL_TIME) {
                        wifiIconId = R.drawable.ic_launcher_notification_clock;
                        reasonTextResource = R.string.notification_wifi_time;
                    } else if (type == ControlType.CONTROL_TOWER || type == ControlType.CONTROL_BGSCAN) {
                        wifiIconId = R.drawable.ic_launcher_notification_cell;
                        reasonTextResource = R.string.notification_wifi_off;
                    } else {
                        wifiIconId = R.drawable.ic_launcher_notification_disabled;
                        reasonTextResource = R.string.notification_swm_disabled;
                    }
                } else if (state == WifiState.WIFI_ON) {
                    wifiTextResource = R.string.notification_title_on;

                    if (type == ControlType.CONTROL_BLUETOOTH) {
                        wifiIconId = R.drawable.ic_launcher_notification_bluetooth;
                        reasonTextResource = R.string.notification_wifi_bt;
                    } else if (type == ControlType.CONTROL_TIME) {
                        wifiIconId = R.drawable.ic_launcher_notification_clock;
                        reasonTextResource = R.string.notification_wifi_time;
                    } else if (type == ControlType.CONTROL_TOWER || type == ControlType.CONTROL_BGSCAN) {
                        // TODO make its own icon
                        wifiIconId = R.drawable.ic_launcher_notification_cell;
                        reasonTextResource = R.string.notification_learning;
                    } else {
                        wifiIconId = R.drawable.ic_launcher_notification_ignore;
                        reasonTextResource = R.string.notification_swm_disabled;
                    }
                }

                notificationBuilder.setSmallIcon(wifiIconId);

                if (wifiTextResource > 0) {
                    notificationBuilder.setContentTitle(getString(wifiTextResource));
                } else {
                    notificationBuilder.setContentTitle("");
                }

                if (reasonTextResource > 0) {
                    notificationBuilder.setContentText(getString(reasonTextResource));
                } else {
                    notificationBuilder.setContentText("");
                }

                if (showNotification)
                    notificationManager.notify(0, notificationBuilder.build());
            }
        });

        towerCleanupTask.run();

        // Force a receiver, doesn't seem to work from manifest
        LogAlias.d("smarter", "rxservice registering receiver");
        registerReceiver(WifiStateChangedReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    }

    private void configurePermissionListeners() {
        telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CELL_INFO | PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    private void removePermissionListeners() {
        telephonyManager.listen(phoneListener, 0);
    }

    private BroadcastReceiver WifiStateChangedReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Smarter Wi-Fi network lock");

            int extraWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE ,
                    WifiManager.WIFI_STATE_UNKNOWN);

            LogAlias.d("smarter", "rx: Wifi_state_changed");

            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            int oldWifiState = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

            LogAlias.d("smarter", "wifi_state_changed old: " + oldWifiState + " new: " + wifiState);

            if ((oldWifiState != WifiManager.WIFI_STATE_ENABLED && oldWifiState != WifiManager.WIFI_STATE_ENABLING) &&
                    (wifiState == WifiManager.WIFI_STATE_ENABLING || wifiState == WifiManager.WIFI_STATE_ENABLED)) {
                LogAlias.d("smarter", "Generating event: Wifi enabled");

                wl.acquire();
                eventBus.post(new EventWifiState(true));
                wl.release();

            } else if ((oldWifiState != WifiManager.WIFI_STATE_DISABLING && oldWifiState != WifiManager.WIFI_STATE_DISABLED) &&
                    (wifiState == WifiManager.WIFI_STATE_DISABLED || wifiState == WifiManager.WIFI_STATE_DISABLING)) {
                LogAlias.d("smarter", "Generating event: Wifi disabled");

                wl.acquire();
                eventBus.post(new EventWifiState(false));
                wl.release();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        eventBus.unregister(this);
        unregisterReceiver(networkReceiver);

        shutdown = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void updateTimeRanges() {
        ArrayList<SmarterTimeRange> ranges = getTimeRangeList();

        currentTimeRange = null;
        nextTimeRange = null;

        if (ranges == null) {
            LogAlias.d("smarter", "updateTimeRanges, no ranges");
            return;
        }

        // Are we in a time range right now?  If so, figure out which one has the
        // shortest duration that we're part of.  Once we fall out of this time range
        // we'll redo this calculation, which will grab the outer time range if one
        // exists.
        for (SmarterTimeRange t : ranges) {
            if (!t.getEnabled())
                continue;

            if (!t.isInRangeNow())
                continue;

            if (currentTimeRange == null) {
                currentTimeRange = new SmarterTimeRange(t);
                continue;
            }

            if (t.getDurationMinutes() < currentTimeRange.getDurationMinutes()) {
                currentTimeRange = t;
            }
        }

        long now = System.currentTimeMillis();
        long timeUtilStart = 0;

        // Figure out the next time range we're going to be in
        for (SmarterTimeRange t : ranges) {
            if (!t.getEnabled())
                continue;

            long nextT = t.getNextStartMillis();

            // Shouldn't be possible since next will always find based on now, but can't hurt
            if (nextT < now)
                continue;

            if (timeUtilStart == 0 || nextT - now < timeUtilStart) {
                nextTimeRange = t;
            }
        }

        if (currentTimeRange == null && nextTimeRange == null) {
            LogAlias.d("smarter", "Not in any time ranges and none coming up");
            return;
        }

        if (currentTimeRange != null) {
            LogAlias.d("smarter", "currently in a time range");
            // Is the next alarm for the end of this time range, or for an overlapping range?
            if (nextTimeRange == null ||
                    (nextTimeRange != null && currentTimeRange.getNextEndMillis() < nextTimeRange.getNextStartMillis())) {
                LogAlias.d("smarter", "next alarm for end of this time range");
                alarmReceiver.setAlarm(this, currentTimeRange.getNextEndMillis() + 1);
            } else {
                LogAlias.d("smarter", "next alarm for start of overlapping time range");
                alarmReceiver.setAlarm(this, nextTimeRange.getNextStartMillis() + 1);
            }

            if (currentTimeRange.getBluetoothControlled() && btAdapter != null) {
                if (currentTimeRange.getBluetoothEnabled()) {
                    btAdapter.enable();
                } else {
                    btAdapter.disable();
                }
            }
        } else if (nextTimeRange != null) {
            LogAlias.d("smarter", "upcoming time range, setting alarm for start of it");
            alarmReceiver.setAlarm(this, nextTimeRange.getNextStartMillis() + 1);
        }
    }

    @Subscribe
    public void onEvent(EventPreferencesChanged ev) {
        everBeenRun = preferences.getBoolean("everbeenrun", false);

        ignoreOpenNetworks = preferences.getBoolean(getString(R.string.prefs_item_ignore_open), false);

        LogAlias.d("smarter", "ignoring open networks; " + ignoreOpenNetworks);

        learnWifi = preferences.getBoolean(getString(R.string.pref_learn), true);

        if (getRunningAsSecondaryUser()) {
            Log.d("smarter", "We're running as a secondary user, disabling");
            proctorWifi = false;
        } else {
            proctorWifi = preferences.getBoolean(getString(R.string.pref_enable), true);
        }

        disableWaitSeconds = Integer.parseInt(preferences.getString(getString(R.string.prefs_item_shutdowntime), "60"));

        if (disableWaitSeconds < 30)
            disableWaitSeconds = 30;

        showNotification = preferences.getBoolean(getString(R.string.prefs_item_notification), true);

        if (!showNotification)
            notificationManager.cancel(0);
        else
            notificationManager.notify(0, notificationBuilder.build());

        // Get wifi config
        useWifiScan = preferences.getBoolean(getString(R.string.prefs_item_use_background), false);

        // Always perform tower purging / location management
        // performTowerPurges = preferences.getBoolean(getString(R.string.prefs_item_towermaintenance), true);
        performTowerPurges = true;

        // Set the alarm if we're going into aggressive checking mode.  Let the old alarm time out if we're disabling it.
        if (preferences.getBoolean(getString(R.string.prefs_item_aggressive), true)) {
            if (!aggressiveTowerCheck) {
                // Set the alarm if it isn't set already
                setAggressiveAlarm();
            }

            aggressiveTowerCheck = true;
        } else {
            aggressiveTowerCheck = false;
        }

        if (preferences.getBoolean(getString(R.string.prefs_item_aggressive_wifi_background), false)) {
            if (!aggressiveWifiScan) {
                setAggressiveWifiAlarm();
            }

            aggressiveWifiScan = true;
        } else {
            aggressiveWifiScan = false;
        }

        configureWifiState();
    }

    public void shutdownService() {
        shutdown = true;
        timerHandler.removeCallbacks(towerCleanupTask);

        cancelWifiDownAlarm();
        cancelWifiUpAlarm();

        // timerHandler.removeCallbacks(wifiEnableTask);
        // timerHandler.removeCallbacks(wifiDisableTask);
        telephonyManager.listen(phoneListener, 0);
    }

    public void cancelWifiDownAlarm() {
        LogAlias.d("smarter", "cancelWifiDownAlarm() cancelling alarm");
        if (wifiDownIntent != null) {
            alarmManager.cancel(wifiDownIntent);
            wifiDownIntent = null;
        }

        pendingWifiShutdown = false;
    }

    public void cancelWifiUpAlarm() {
        LogAlias.d("smarter", "cancelWifiUpAlarm()");
        if (wifiUpIntent != null) {
            alarmManager.cancel(wifiUpIntent);
            wifiUpIntent = null;
        }
    }

    private void startBluetoothEnable() {
        if (btAdapter != null) {
            LogAlias.d("smarter", "Turning on bluetooth");
            btAdapter.enable();
        }
    }

    private void startBluetoothShutdown() {
        if (btAdapter != null) {
            LogAlias.d("smarter", "Turning off bluetooth");
            btAdapter.disable();
        }
    }

    @SuppressLint("newapi")
    private void startWifiEnable() {
        pendingWifiShutdown = false;

        // timerHandler.removeCallbacks(wifiEnableTask);

        if (wifiDownIntent != null) {
            LogAlias.d("smarter", "startWifiEnable(), cancelling pending down alarm");
            alarmManager.cancel(wifiDownIntent);
            wifiDownIntent = null;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            LogAlias.d("smarter", "turning on wifi immediately because screen is off");

            LogAlias.d("smarter", "Cancelling pending up alarm");
            cancelWifiUpAlarm();

            wifiManager.setWifiEnabled(true);
            return;
        }

        // timerHandler.postDelayed(wifiEnableTask, enableWaitSeconds * 1000);

        if (wifiUpIntent != null) {
            LogAlias.d("smarter", "Already trying to bring up wifi, not scheduling another bringup");
        } else {
            Intent i = new Intent(context, AlarmReceiver.class);

            i.putExtra(AlarmReceiver.EXTRA_WIFIUP, enableWaitSeconds);
            wifiUpIntent = PendingIntent.getBroadcast(context, 1000, i, PendingIntent.FLAG_UPDATE_CURRENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // use the M wakeup even in idle mode, if we can
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (enableWaitSeconds * 1000), wifiUpIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (enableWaitSeconds * 1000), wifiUpIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (enableWaitSeconds * 1000), wifiUpIntent);
            }

            LogAlias.d("smarter", "Starting countdown of " + enableWaitSeconds + " to enable wifi");
        }

        // alarmReceiver.setWifiUpAlarm(context, enableWaitSeconds);
    }

    private void startWifiShutdown() {
        LogAlias.d("smarter", "startWifiShutdown()");

        cancelWifiUpAlarm();

        /* no longer shut off immediately when the screen is off, it might turn off wifi
         * really aggressively in bssid mode

        // If we're asked to turn off wifi and the screen is off, just do it.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (!pm.isScreenOn()) {
            LogAlias.d("smarter", "shutting down wifi immediately because screen is off");

            pendingWifiShutdown = false;

            cancelWifiDownAlarm();

            // alarmReceiver.cancelWifiDownAlarm(context);
            // timerHandler.removeCallbacks(wifiDisableTask);

            wifiManager.setWifiEnabled(false);
            return;
        }
        */

        if (wifiDownIntent != null) {
            LogAlias.d("smarter", "Already trying to bring down wifi, not scheduling another bringdown");
        } else {
            Intent i = new Intent(context, AlarmReceiver.class);

            i.putExtra(AlarmReceiver.EXTRA_WIFIDOWN, disableWaitSeconds);
            wifiDownIntent = PendingIntent.getBroadcast(context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // use the M wakeup even in idle mode, if we can
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * disableWaitSeconds), wifiDownIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Use exact timing if we have it
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * disableWaitSeconds), wifiDownIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * disableWaitSeconds), wifiDownIntent);
            }

            pendingWifiShutdown = true;

            LogAlias.d("smarter", "Starting countdown of " + disableWaitSeconds + " to shut down wifi");
        }
    }

    public void doWifiEnable() {
        LogAlias.d("smarter", "doWifiEnable() wifi enable task triggered");

        cancelWifiUpAlarm();
        cancelWifiDownAlarm();

        if (shutdown) return;

        if (!proctorWifi) return;

        LogAlias.d("smarter", "enabling wifi");
        wifiManager.setWifiEnabled(true);
    }

    public void doWifiDisable() {
        LogAlias.d("smarter", "doWifiDisable() wifi disable task triggered");

        cancelWifiUpAlarm();
        cancelWifiDownAlarm();

        if (shutdown) {
            LogAlias.d("smarter", "Wifi disable task triggered, but we're about to shut down the service");
            pendingWifiShutdown = false;
            return;
        }

        // If we're not proctoring wifi...
        if (!proctorWifi) {
            LogAlias.d("smarter", "We were going to shut down wifi, but we're no longer controlling wi-fi");
            pendingWifiShutdown = false;
            return;
        }

        if (getWifiState() == WifiState.WIFI_ON) {
            LogAlias.d("smarter", "We were going to shut down wifi, but it's connected now");
            pendingWifiShutdown = false;
            return;
        }

        LogAlias.d("smarter", "Shutting down wi-fi, we haven't gotten a link");
        wifiManager.setWifiEnabled(false);

        pendingWifiShutdown = false;
    }

    private Runnable wifiEnableTask = new Runnable() {
        public void run() {
            if (shutdown) return;

            if (!proctorWifi) return;

            LogAlias.d("smarter", "enabling wifi");
            wifiManager.setWifiEnabled(true);
        }
    };

    private Runnable wifiDisableTask = new Runnable() {
        public void run() {
            LogAlias.d("smarter", "wifi disable task triggered");

            if (shutdown) {
                LogAlias.d("smarter", "Wifi disable task triggered, but we're about to shut down the service");
                pendingWifiShutdown = false;
                return;
            }

            // If we're not proctoring wifi...
            if (!proctorWifi) {
                LogAlias.d("smarter", "We were going to shut down wifi, but we're no longer controlling wi-fi");
                pendingWifiShutdown = false;
                return;
            }

            if (getWifiState() == WifiState.WIFI_ON) {
                LogAlias.d("smarter", "We were going to shut down wifi, but it's connected now");
                pendingWifiShutdown = false;
                return;
            }

            LogAlias.d("smarter", "Shutting down wi-fi, we haven't gotten a link");
            wifiManager.setWifiEnabled(false);

            pendingWifiShutdown = false;
        }
    };

    private void setAggressiveWifiAlarm() {
        LogAlias.d("smarter", "Setting timer to wake up and check wifi");

        Intent i = new Intent(context, AlarmReceiver.class);

        i.putExtra(AlarmReceiver.EXTRA_WIFI_AGGRESSIVE, 60);

        PendingIntent wupi = PendingIntent.getBroadcast(context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // use the M wakeup even in idle mode, if we can
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        }
    }

    public void doAggressiveWifiCheck() {
        // Force a handle
        handleWifiScan();

        // Schedule another scan
        if (wifiManager != null) {
            wifiManager.startScan();
        }

        // Set the alarm to look again
        if (aggressiveWifiScan) {
            setAggressiveAlarm();
        }
    }

    private void setAggressiveAlarm() {
        LogAlias.d("smarter", "Setting timer to wake up and check towers");

        Intent i = new Intent(context, AlarmReceiver.class);

        i.putExtra(AlarmReceiver.EXTRA_AGGRESSIVE, 60);

        PendingIntent wupi = PendingIntent.getBroadcast(context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // use the M wakeup even in idle mode, if we can
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + (1000 * 60), wupi);
        }
    }

    public void doAggressiveCheck() {
        // Look up the current location
        handleCellLocation(null);

        // Set the alarm to look again
        if (aggressiveTowerCheck) {
            setAggressiveAlarm();
        }
    }

    // Set current tower or fetch current tower
    private void handleCellLocation(CellLocationCommon location) {
        checkForPermissions();

        if (!sufficientPermissions)
            return;

        if (location == null) {
            location = new CellLocationCommon(telephonyManager.getCellLocation());
        }

        setCurrentTower(location);
    }

    @Subscribe
    public void onEvent(EventCellTower c) {
        if (c != null)
            setCurrentTower(c.getLocation());
    }

    @Produce
    public EventCellTower produceCellLocation() {
        checkForPermissions();

        if (sufficientPermissions) {
            LogAlias.d("smarter", "service produceCellLocation triggered");
            return new EventCellTower(telephonyManager.getCellLocation());
        } else {
            return null;
        }
    }

    @Subscribe
    public void onEvent(EventWifiConnected e) {
        LogAlias.d("smarter", "BUS - Wifi Connected");

        if (pausedNewNetwork) {
            LogAlias.d("smarter", "connected to a network, no longer paused waiting to connect.");
            pausedNewNetwork = false;
        }

        worldState.setWifiInfo(e.getWifiInfo());

        // Get the SSID and encryption
        SmarterSSID ssid = getCurrentSsid();

        // Log the BSSID if we're able to
        if (ssid != null && !ssid.isBlacklisted() && (ssid.isEncrypted() || !ignoreOpenNetworks)) {
            LogAlias.d("smarter", "Logging BSSID " + ssid.getBssid() + " for " + ssid.getSsid());
            dbSource.mapBssid(ssid);
        }


        configureWifiState();
    }

    @Produce
    public EventWifiConnected produceWifiConnected() {
        WifiInfo wi = wifiManager.getConnectionInfo();

        if (wi != null && wi.getSupplicantState() == SupplicantState.COMPLETED) {
            LogAlias.d("smarter", "service produceWifiConnected " + wi.toString());
            return new EventWifiConnected(wi);
        }

        LogAlias.d("smarter", "service produceWifiConnected null, not connected");
        return null;
    }

    @Subscribe
    public void onEvent(EventWifiDisconnected e) {
        LogAlias.d("smarter", "BUS - eventwifidisconnected");

        worldState.setWifiInfo(null);

        configureWifiState();
    }

    @Produce
    public EventWifiDisconnected produceWifiDisconnected() {
        int state = wifiManager.getWifiState();

        if (state == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wi = wifiManager.getConnectionInfo();

            if (wi == null) {
                LogAlias.d("smarter", "service produceWifiDisconnected wifi enabled, connection null, producing disabled");
                return new EventWifiDisconnected();
            }

            if (wi.getSupplicantState() != SupplicantState.COMPLETED) {
                LogAlias.d("smarter", "service produceWifiDisconnected wifi enabled, but supplicant not completed, producing disabled: " + wi);
                return new EventWifiDisconnected();
            }
        }

        LogAlias.d("smarter", "service produceWifiDisconnected wifi not enabled, or enabled and we were connected, not producing anything.  state: " + state);
        return null;

    }

    @Subscribe
    public void onEvent(EventWifiState e) {
        LogAlias.d("smarter", "BUS - EventWifiState");

        worldState.setWifiEnabled(e.isEnabled());

        configureWifiState();
    }


    @Produce
    public EventWifiState produceWifiState() {
        int state = wifiManager.getWifiState();

        if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING) {
            LogAlias.d("smarter", "service produceWifiDisabled state enabled or enabling");
            return new EventWifiState(true);
        }

        LogAlias.d("smarter", "service produceWifiDisabled state disabled, disabling, or unknown");
        return new EventWifiState(false);
    }

    // Set the current tower and figure out what our tower state is
    private void setCurrentTower(CellLocationCommon curloc) {
        checkForPermissions();

        if (!sufficientPermissions)
            return;

        if (curloc == null) {
            curloc = new CellLocationCommon(telephonyManager.getCellLocation());
        }

        // If we still can't get a location somehow
        if (curloc == null) {
            LogAlias.d("smarter", "Got invalid tower");
            worldState.setCellTowerType(CellLocationCommon.TowerType.TOWER_INVALID);
            worldState.setCellLocation(new CellLocationCommon());
        }

        /*
        if (curloc.equals(worldState.getCellLocation())) {
            LogAlias.d("smarter", "Ignoring cell location event, identical to current position");
            return;
        }
        */

        worldState.setCellLocation(curloc);

        if (!curloc.isValid())
            worldState.setCellTowerType(CellLocationCommon.TowerType.TOWER_INVALID);
        else
            worldState.setCellTowerType(CellLocationCommon.TowerType.TOWER_UNKNOWN);

        if (curloc.isValid() && proctorWifi) {
            SmarterSSID ssid = getCurrentSsid();

            // We've been here before
            if (dbSource.queryTowerMapped(curloc.getTowerId())) {
                LogAlias.d("smarter", "In range of known tower: " + curloc.getTowerId() + " towertype = enable");
                worldState.setCellTowerType(CellLocationCommon.TowerType.TOWER_ENABLE);

                // If the SSID makes sense, isn't blacklisted, and isn't open, update our tower records
                if (ssid != null && !ssid.isBlacklisted() && (ssid.isEncrypted() || !ignoreOpenNetworks)) {
                    LogAlias.d("smarter", "Updating known tower " + curloc.getTowerId() + " for ssid " + ssid.getSsid());
                    dbSource.mapTower(ssid, curloc.getTowerId());
                }
            } else {
                if ((ssid != null && !ssid.isBlacklisted() && (ssid.isEncrypted() || !ignoreOpenNetworks)) && getWifiState() == WifiState.WIFI_ON &&
                        targetState == WifiState.WIFI_ON && !getWifiTethered()) {
                    // We're connected, we want to stay turned on, learn this tower.  Don't map while we're tethered.
                    LogAlias.d("smarter", "New tower " + curloc.getTowerId() + ", Wi-Fi connected ssid " + ssid.getSsid() + ", learning tower");
                    dbSource.mapTower(ssid, curloc.getTowerId());
                    lastTowerMap = System.currentTimeMillis();
                    worldState.setCellTowerType(CellLocationCommon.TowerType.TOWER_ENABLE);
                }
            }
        }

        configureWifiState();
        triggerCallbackTowerChanged();
    }

    public void addCallback(SmarterServiceCallback cb) {
        try {
            if (cb == null) {
                Log.e("smarter", "Got a null callback?");
                return;
            }

            synchronized (callbackList) {
                callbackList.add(cb);
            }

            // Call our CBs immediately for setup
            cb.towerStateChanged(worldState.getCellLocation().getTowerId(), worldState.getCellTowerType());
            cb.wifiStateChanged(getCurrentSsid(), curState, targetState, lastControlReason);
            cb.bluetoothStateChanged(getBluetoothState());
        } catch (NullPointerException npe) {
            Log.e("smarter", "Got NPE in addcallback, caught, but not sure what happened");
        }

    }

    public void removeCallback(SmarterServiceCallback cb) {
        if (cb == null) {
            Log.e("smarter", "Got a null callback?");
            return;
        }

        synchronized (callbackList) {
            callbackList.remove(cb);
        }
    }

    public void triggerCallbackTowerChanged() {
       synchronized (callbackList) {
           for (SmarterServiceCallback cb : callbackList) {
               cb.towerStateChanged(worldState.getCellLocation().getTowerId(), worldState.getCellTowerType());
           }
       }
    }

    public void triggerCallbackWifiChanged() {
        synchronized (callbackList) {
            for (SmarterServiceCallback cb: callbackList) {
                cb.wifiStateChanged(getCurrentSsid(), curState, targetState, lastControlReason);
            }
        }
    }

    public void triggerCallbackBluetoothChanged() {
        synchronized (callbackList) {
            for (SmarterServiceCallback cb: callbackList) {
                cb.bluetoothStateChanged(getBluetoothState());
            }
        }
    }

    public void configureWifiState() {
        previousState = curState;

        curState = getWifiState();
        targetState = getShouldWifiBeEnabled();

        LogAlias.d("smarter", "World state: " + worldState.toString());
        LogAlias.d("smarter", "configureWifiState previous " + previousState + " current " + curState + " target " + targetState);

        // If we're ignoring wifi we do nothing
        if (curState == WifiState.WIFI_IGNORE) {
            triggerCallbackWifiChanged();
            return;
        }

        // If we're on/idle, allow it to be shut down unless blocked.  If we're
        // *connected*, don't let it shut us down
        if (curState == WifiState.WIFI_ON || curState == WifiState.WIFI_IDLE) {
            if (targetState == WifiState.WIFI_BLOCKED) {
                LogAlias.d("smarter", "Target state: Blocked, shutting down wifi now, " + lastControlReason);

                cancelWifiDownAlarm();
                cancelWifiUpAlarm();

                pendingWifiShutdown = false;

                wifiManager.setWifiEnabled(false);
            } else if (targetState == WifiState.WIFI_OFF) {
                LogAlias.d("smarter", "Target state: Off, scheduling shutdown, " + lastControlReason);

                // Kill any enable pending
                // timerHandler.removeCallbacks(wifiEnableTask);
                cancelWifiUpAlarm();

                // Start the timered kill
                startWifiShutdown();
            }
        } else {
            if (targetState == WifiState.WIFI_ON) {
                LogAlias.d("smarter", "Target state: On, scheduling bringup, " + lastControlReason);
                startWifiEnable();
            }
        }

        triggerCallbackWifiChanged();
    }

    public void configureBluetoothState() {
        int btstate = btAdapter.getState();
        BluetoothState targetstate = getShouldBluetoothBeEnabled();

        // Learn time range if null
        if (currentTimeRange == null) {
            initialBluetoothState = (btstate != BluetoothAdapter.STATE_OFF);
            LogAlias.d("smarter", "learned default bt state: " + initialBluetoothState);
        }

        if (btstate == BluetoothAdapter.STATE_OFF) {
            bluetoothBlocking = false;
            bluetoothEnabled = false;
            bluetoothBlockingDevices.clear();
            bluetoothConnectedDevices.clear();

            if (targetstate == BluetoothState.BLUETOOTH_ON) {
                startBluetoothEnable();
            }
        } else if (btstate == BluetoothAdapter.STATE_ON) {
            bluetoothEnabled = true;

            if (targetstate == BluetoothState.BLUETOOTH_BLOCKED ||
                    targetstate == BluetoothState.BLUETOOTH_OFF) {
                startBluetoothShutdown();
            }
        }

        triggerCallbackBluetoothChanged();

        // We can't get a list of connected devices, only watch
    }

    public void configureTimerangeState() {
        boolean wasInRange = false;

        // Are we in a state when we started?  If not, update our bluetooth state
        if (currentTimeRange == null) {
            initialBluetoothState = getBluetoothState() != BluetoothState.BLUETOOTH_OFF;
            LogAlias.d("smarter", "not in a range, learned default bt state: " + initialBluetoothState);
        } else {
            wasInRange = true;
        }

        LogAlias.d("smarter", "updating time ranges");
        // Figure out if we should be in one and set future alarms
        updateTimeRanges();

        // We've transitioned out of a time range so set BT back to whatever it used to be
        if (currentTimeRange == null && wasInRange && btAdapter != null) {
            LogAlias.d("smarter", "transitioning out of time range, restoring bluetooth to previous state of: " + initialBluetoothState);
            if (initialBluetoothState) {
                btAdapter.enable();
            } else {
                btAdapter.disable();
            }
        }

        // Configure wifi and bluetooth
        configureWifiState();
        configureBluetoothState();
    }

    public void handleWifiP2PState(int state) {
        LogAlias.d("smarter", "wifi p2p state changed: " + state);
    }

    public void handleBluetoothDeviceState(BluetoothDevice d, int state) {
        if (state == BluetoothAdapter.STATE_CONNECTED) {
            SmarterBluetooth sbd = dbSource.getBluetoothBlacklisted(d);

            bluetoothConnectedDevices.put(d.getAddress(), sbd);

            if (sbd.isBlacklisted()) {
                LogAlias.d("smarter", "blocking bt on device " + d.getAddress() + " " + d.getName());
                bluetoothBlockingDevices.put(d.getAddress(), sbd);

                if (!bluetoothBlocking) {
                    bluetoothBlocking = true;
                    configureWifiState();
                }
            }
        } else {
            bluetoothConnectedDevices.remove(d.getAddress());
            bluetoothBlockingDevices.remove(d.getAddress());

            if (bluetoothBlockingDevices.size() <= 0) {
                bluetoothBlocking = false;
                configureWifiState();
            }
        }
    }

    // Based on everything we know, should bluetooth be enabled?
    public BluetoothState getShouldBluetoothBeEnabled() {
        // We're not looking at all
        if (proctorWifi == false) {
            lastControlReason = ControlType.CONTROL_DISABLED;
            return BluetoothState.BLUETOOTH_IGNORE;
        }

        // Are we in a time range?
        if (currentTimeRange != null) {
            if (currentTimeRange.getBluetoothControlled()) {
                // Does this time range control bluetooth?
                if (currentTimeRange.getBluetoothEnabled())
                    return BluetoothState.BLUETOOTH_ON;
                else
                    return BluetoothState.BLUETOOTH_BLOCKED;
            } else {
                // Otherwise ignore it
                return BluetoothState.BLUETOOTH_IGNORE;
            }
        }

        /* Otherwise ignore */
        return BluetoothState.BLUETOOTH_IGNORE;

        /*
        // Otherwise return us to the state we were before we started this
        return initialBluetoothState ? BluetoothState.BLUETOOTH_ON : BluetoothState.BLUETOOTH_BLOCKED;
        */
    }

    // Based on everything we know, should wifi be enabled?
    // WIFI_ON - Turn it on
    // WIFI_OFF - Start a shutdown timer
    // WIFI_BLOCKED - Kill it immediately
    // WIFI_IDLE - Do nothing
    public WifiState getShouldWifiBeEnabled() {
        // We've never been run
        if (everBeenRun == false) {
            lastControlReason = ControlType.CONTROL_NEVERRUN;
            return WifiState.WIFI_IGNORE;
        }

        checkForPermissions();

        // We don't have permissions
        if (sufficientPermissions == false) {
            lastControlReason = ControlType.CONTROL_PERMISSIONS;
            return WifiState.WIFI_IDLE;
        }

        WifiState curstate = getWifiState();
        SmarterSSID ssid = getCurrentSsid();
        boolean tethered = getWifiTethered();

        // We're not looking at all
        if (proctorWifi == false) {
            lastControlReason = ControlType.CONTROL_DISABLED;
            return WifiState.WIFI_IGNORE;
        }

        // Tethering overrides almost everything
        if (tethered) {
            LogAlias.d("smarter", "Tethering detected, ignoring wifi state");
            lastControlReason = ControlType.CONTROL_TETHER;
            return WifiState.WIFI_IGNORE;
        }

        // Airplane mode causes us to ignore the wifi entirely, do whatever the user sets it as
        if (getAirplaneMode()) {
            LogAlias.d("smarter", "Airplane mode detected, ignoring wifi state");
            lastControlReason = ControlType.CONTROL_AIRPLANE;
            return WifiState.WIFI_IGNORE;
        }

        if (pausedNewNetwork) {
            LogAlias.d("smarter", "Pausing to add a new network");
            lastControlReason = ControlType.CONTROL_PAUSED;
            return WifiState.WIFI_ON;
        }

        // If the user wants specifically to turn it on or off via the SWM UI, do so
        if (userOverrideState == WifiState.WIFI_OFF) {
            LogAlias.d("smarter", "User-controled wifi, user wants wifi off");
            lastControlReason = ControlType.CONTROL_USER;
            return WifiState.WIFI_BLOCKED;
        }

        if (userOverrideState == WifiState.WIFI_ON) {
            LogAlias.d("smarter", "User-controlled wifi, user wants wifi on");
            lastControlReason = ControlType.CONTROL_USER;
            return WifiState.WIFI_ON;
        }

        // If we're in a time range...
        if (currentTimeRange != null) {
            // And we control wifi...
            if (currentTimeRange.getWifiControlled()) {
                // and we're supposed to shut it down
                if (!currentTimeRange.getWifiEnabled()) {
                    // Always aggressively block when in a time range
                    LogAlias.d("smarter", "Time range, aggressively disabling wifi");

                    lastControlReason = ControlType.CONTROL_TIME;
                    return WifiState.WIFI_BLOCKED;

                    /*
                    // Harsh or gentle?
                    if (currentTimeRange.getAggressiveManagement())
                        return WifiState.WIFI_BLOCKED;
                    else
                        return WifiState.WIFI_OFF;
                        */
                } else {
                    // We want it on..
                    LogAlias.d("smarter", "Time range, enabling wifi");

                    lastControlReason = ControlType.CONTROL_TIME;
                    return WifiState.WIFI_ON;
                }
            }

            // Otherwise we're not managing wifi in this time range so we keep going
        }

        // Bluetooth blocks learning
        if (bluetoothBlocking) {
            LogAlias.d("smarter", "Connected to bluetooth device, blocking wifi");
            lastControlReason = ControlType.CONTROL_BLUETOOTH;
            return WifiState.WIFI_BLOCKED;
        }

        // If we're ignoring this SSID
        if (curstate == WifiState.WIFI_ON && (ssid != null && ssid.isBlacklisted())) {
            LogAlias.d("smarter", "Connected to blacklisted SSID, ignoring wifi");
            lastControlReason = ControlType.CONTROL_SSIDBLACKLIST;
            return WifiState.WIFI_IGNORE;
        }

        // If we're ignoring unencrypted SSIDs
        if (curstate == WifiState.WIFI_ON && (ssid != null && (ssid.isOpen() && ignoreOpenNetworks))) {
            LogAlias.d("smarter", "Connected to open SSID, ignoring wifi");
            lastControlReason = ControlType.CONTROL_OPEN;
            return WifiState.WIFI_IGNORE;
        }

        // If we use wifi scan mode...
        if (useWifiScan) {
            // If we're in wifi mode look at the bgscan
            if (worldState.getEnableViaScan()) {
                LogAlias.d("smarter", "in range of bssid we've used before, wifi should be on.");
                lastControlReason = ControlType.CONTROL_BGSCAN;
                return WifiState.WIFI_ON;
            } else {
                LogAlias.d("smarter", "Shouldwifibeenabled - Not in range of any BSSIDs we've used before.");
                List<ScanResult> sr = worldState.getScan_results();
                LogAlias.d("smarter", "We knew about " + sr.size() + " BSSIDs");
            }

            lastControlReason = ControlType.CONTROL_BGSCAN;
        } else {
            if (worldState.getCellTowerType() == CellLocationCommon.TowerType.TOWER_INVALID) {
                LogAlias.d("smarter", "Connected to invalid tower, ignoring wifi state");
                lastControlReason = ControlType.CONTROL_TOWER;
                return WifiState.WIFI_IGNORE;
            }

            if (worldState.getCellTowerType() == CellLocationCommon.TowerType.TOWER_BLOCK) {
                LogAlias.d("smarter", "Connected to blocked tower " + worldState.getCellLocation().getTowerId() + ", wifi should be off");
                lastControlReason = ControlType.CONTROL_TOWERID;
                return WifiState.WIFI_BLOCKED;
            }

            // If we're in tower mode look at the towers
            if (worldState.getCellTowerType() == CellLocationCommon.TowerType.TOWER_ENABLE) {
                LogAlias.d("smarter", "Connected to enable tower " + worldState.getCellLocation().getTowerId() + ", wifi should be on");
                lastControlReason = ControlType.CONTROL_TOWER;
                return WifiState.WIFI_ON;
            }

            if (worldState.getCellTowerType() == CellLocationCommon.TowerType.TOWER_UNKNOWN && curstate == WifiState.WIFI_ON) {
                LogAlias.d("smarter", "Connected to unknown tower " + worldState.getCellLocation().getTowerId() + ", wifi is enabled, keep wifi on");
                lastControlReason = ControlType.CONTROL_TOWER;
                return WifiState.WIFI_ON;
            }

            if (worldState.getCellTowerType() == CellLocationCommon.TowerType.TOWER_UNKNOWN && curstate == WifiState.WIFI_IDLE) {
                LogAlias.d("smarter", "Connected to unknown tower " + worldState.getCellLocation().getTowerId() + ", wifi is idle, we should turn it off.");
                lastControlReason = ControlType.CONTROL_TOWER;
                return WifiState.WIFI_OFF;
            }

            lastControlReason = ControlType.CONTROL_TOWER;
        }

        return WifiState.WIFI_OFF;
    }

    public BluetoothState getBluetoothState() {
        if (btAdapter == null)
            return BluetoothState.BLUETOOTH_OFF;

        int s =  btAdapter.getState();

        if (s == BluetoothAdapter.STATE_ON)
            return BluetoothState.BLUETOOTH_ON;

        return BluetoothState.BLUETOOTH_OFF;
    }

    public WifiState getWifiState() {
        if (!proctorWifi) {
            lastControlReason = ControlType.CONTROL_DISABLED;
            return WifiState.WIFI_IGNORE;
        }

        int rawstate = wifiManager.getWifiState();

        boolean rawwifienabled = false;

        // If we're turned on or in the process of turning on
        if (rawstate == WifiManager.WIFI_STATE_ENABLED || rawstate == WifiManager.WIFI_STATE_ENABLING) {
            rawwifienabled = true;
        } else {
            // Otherwise we're just off, stop looking
            return WifiState.WIFI_OFF;
        }

        // Ask connectivitymanager what we're doing
        NetworkInfo rawni = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        boolean rawnetenabled = false;

        if (rawni != null && rawni.isConnectedOrConnecting())
            rawnetenabled = true;

        // LogAlias.d("smarter", "getwifistate wifi radio enable: " + rawwifienabled + " isConnected " + rawnetenabled);

        // Connectivity manager says we're connected (or in the process of connecting) so we're fully 'on'
        if (rawnetenabled) {
            return WifiState.WIFI_ON;
        }

        // Wifi interface is turned on, but we're not connected
        if (rawwifienabled) {
            return WifiState.WIFI_IDLE;
        }

        return WifiState.WIFI_OFF;
    }

    public SmarterSSID getCurrentSsid() {
        SmarterSSID curssid = null;

        // Populate an intelligently filled in SSID
        if (getWifiState() == WifiState.WIFI_ON) {
            curssid = new SmarterSSID(wifiManager, dbSource);
        }

        /*
        if (getWifiState() == WifiState.WIFI_ON)
            curssid = dbSource.getSsidBlacklisted(wifiManager.getConnectionInfo().getSSID());
            */

        return curssid;
    }

    public Long getCurrentTower() {
        return worldState.getCellLocation().getTowerId();
    }

    public boolean getAirplaneMode() {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    public int getSleepPolicy() {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY, Settings.System.WIFI_SLEEP_POLICY_NEVER);
    }

    public boolean getWifiStateEnabled(WifiState state) {
        if (state == WifiState.WIFI_ON || state == WifiState.WIFI_IDLE)
            return true;

        return false;
    }

    // Convert the current state to text, with fill-ins on formatting, etc
    public String currentStateToComplexText() {
        if (getRunningAsSecondaryUser()) {
            return getString(R.string.simple_explanation_multiuser);
        }

        if (curState == WifiState.WIFI_BLOCKED) {
            if (lastControlReason == ControlType.CONTROL_BLUETOOTH) {
                return getString(R.string.simple_explanation_bt);
            } else if (lastControlReason == ControlType.CONTROL_TIME) {
                if (currentTimeRange == null) {
                    return "We think we're in a time range but something is wrong.";
                }

                return String.format(getString(R.string.simple_explanation_time), getString(R.string.timerange_control_off),
                        currentTimeRange.getStartHour(), currentTimeRange.getStartMinute(), currentTimeRange.getEndHour(),
                        currentTimeRange.getEndMinute());
            }
        } else if (curState == WifiState.WIFI_IGNORE) {
            if (lastControlReason == ControlType.CONTROL_AIRPLANE) {
                return getString(R.string.simple_explanation_airplane);
            } else if (lastControlReason == ControlType.CONTROL_TETHER) {
                return getString(R.string.simple_explanation_tether);
            } else if (lastControlReason == ControlType.CONTROL_SSIDBLACKLIST) {
                return getString(R.string.simple_explanation_blackssid);
            } else if (lastControlReason == ControlType.CONTROL_DISABLED) {
                return getString(R.string.simple_explanation_disabled);
            } else if (lastControlReason == ControlType.CONTROL_NEVERRUN) {
                return getString(R.string.simple_explanation_neverrun);
            } else if (lastControlReason == ControlType.CONTROL_OPEN) {
                return getString(R.string.simple_explanation_openssid);
            }
        } else if (curState == WifiState.WIFI_ON) {
            if (lastControlReason == ControlType.CONTROL_TIME) {
                if (currentTimeRange == null) {
                    return "We think we're in a time range but something is wrong.";
                }

                return String.format(getString(R.string.simple_explanation_time), getString(R.string.timerange_control_on),
                        currentTimeRange.getStartHour(), currentTimeRange.getStartMinute(), currentTimeRange.getEndHour(),
                        currentTimeRange.getEndMinute());
            } else if (lastControlReason == ControlType.CONTROL_PAUSED) {
                return getString(R.string.simple_explanation_add);
            }

            // Otherwise we're connected, are we learning?
            if (learnWifi) {
                return getString(R.string.simple_explanation_learning);
            }

        } else if (curState == WifiState.WIFI_OFF) {
            // We're trying to get back up
            if (targetState == WifiState.WIFI_ON) {
                return getString(R.string.simple_explanation_bringup);
            }

            if (lastControlReason == ControlType.CONTROL_AIRPLANE) {
                return getString(R.string.simple_explanation_airplane);

            } else if (lastControlReason == ControlType.CONTROL_BLUETOOTH) {
                return getString(R.string.simple_explanation_bt);
            } else if (lastControlReason == ControlType.CONTROL_TIME) {
                if (currentTimeRange == null) {
                    return "We think we're in a time range but something is wrong.";
                }

                return String.format(getString(R.string.simple_explanation_time), getString(R.string.timerange_control_off),
                        currentTimeRange.getStartHour(), currentTimeRange.getStartMinute(), currentTimeRange.getEndHour(),
                        currentTimeRange.getEndMinute());
            } else if (lastControlReason == ControlType.CONTROL_TOWER) {
                return getString(R.string.simple_explanation_off);
            } else if (lastControlReason == ControlType.CONTROL_BGSCAN) {
                return getString(R.string.simple_explanation_bgscan);
            }
        } else if (curState == WifiState.WIFI_IDLE) {
            return getString(R.string.simple_explanation_idle);
        }

        return "Something went weird describing config - state " + curState + " control " + lastControlReason;
    }

    public ArrayList<SmarterBluetooth> getBluetoothBlacklist() {
        ArrayList<SmarterBluetooth> btlist = new ArrayList<SmarterBluetooth>();

        if (btAdapter == null)
            return btlist;

        Set<BluetoothDevice> btset = btAdapter.getBondedDevices();

        for (BluetoothDevice d : btset) {
            SmarterBluetooth sbt = dbSource.getBluetoothBlacklisted(d);

            btlist.add(sbt);
        }

        return btlist;
    }

    public void setBluetoothBlacklist(SmarterBluetooth device, boolean blacklist, boolean enable) {
        dbSource.setBluetoothBlacklisted(device, blacklist, enable);

        if (blacklist) {
            if (!bluetoothBlockingDevices.containsKey(device.getBtmac())) {
                if (bluetoothConnectedDevices.containsKey(device.getBtmac())) {
                    bluetoothBlockingDevices.put(device.getBtmac(), device);
                    bluetoothBlocking = true;

                    LogAlias.d("smarter", "after adding " + device.getBtName() + " blocking bluetooth");

                    configureWifiState();
                }
            }
        } else {
            if (bluetoothBlockingDevices.containsKey(device.getBtmac())) {
                bluetoothBlockingDevices.remove(device.getBtmac());

                if (bluetoothBlockingDevices.size() <= 0) {
                    LogAlias.d("smarter", "after removing " + device.getBtName() + " nothing blocking in bluetooth");
                    bluetoothBlocking = false;
                    configureWifiState();
                }
            }
        }

    }

    public ArrayList<SmarterSSID> getSsidBlacklist() {
        ArrayList<SmarterSSID> blist = new ArrayList<SmarterSSID>();
        List<WifiConfiguration> wic = wifiManager.getConfiguredNetworks();

        if (wic == null) {
            LogAlias.d("smarter", "getssidblacklist - wifimanager configuration list was null");
            return blist;
        }

        for (WifiConfiguration w : wic) {
            blist.add(dbSource.getSsidBlacklisted(w.SSID));
        }

        LogAlias.d("smarter", "blacklist returning list of " + blist.size());
        return blist;
    }

    public void setSsidBlacklist(SmarterSSID ssid, boolean blacklisted) {
        LogAlias.d("smarter", "service backend setting ssid " + ssid.getSsid() + " blacklist " + blacklisted);
        dbSource.setSsidBlacklisted(ssid, blacklisted);
        handleCellLocation(null);
        configureWifiState();
    }

    public ArrayList<SmarterSSID> getSsidLearnedlist() {
        return dbSource.getMappedSSIDList();
    }

    public void deleteSsidTowerMap(SmarterSSID ssid) {
        dbSource.deleteSsidTowerMap(ssid);

        handleCellLocation(null);
    }

    public void deleteBssidMap(SmarterSSID ssid) {
        dbSource.deleteSsidBssidMap(ssid);

        handleCellLocation(null);
    }

    public void deleteCurrentTower() {
        CellLocationCommon loc = worldState.getCellLocation();

        if (loc == null) {
            LogAlias.d("smarter", "tried to delete tower, but current location is null");
            return;
        }

        if (!loc.isValid()) {
            LogAlias.d("smarter", "tried to delete tower, but current location invalid");
            return;
        }

        LogAlias.d("smarter", "trying to delete tower " + loc.getTowerId());
        dbSource.deleteSsidTowerInstance(loc.getTowerId());

        // We don't want to have wifi on until we figure out what's going on
        targetState = WifiState.WIFI_OFF;

        // Clear the current tower to something invalid
        worldState.setCellLocation(new CellLocationCommon());

        handleCellLocation(null);
        configureWifiState();
    }

    public long getLastTowerMap() {
        return lastTowerMap;
    }

    public boolean getWifiTethered() {
        boolean ret = false;
        Method[] wmMethods = wifiManager.getClass().getDeclaredMethods();
        for (Method method: wmMethods){
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    ret = (Boolean) method.invoke(wifiManager);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

        }

        // LogAlias.d("smarter", "tethering: " + ret);
        return ret;
    }

    public boolean getRunningAsSecondaryUser() {
        int ret = 0;

        // Prior to 4.3 we didn't have multiuser at all
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return false;
        }

        UserHandle uh = android.os.Process.myUserHandle();

        Method[] uhMethods = uh.getClass().getDeclaredMethods();
        for (Method method : uhMethods) {
            if (method.getName().equals("myUserId")) {
                try {
                    ret = (Integer) method.invoke(uh);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }

        if (ret == 0) {
            return false;
        }

        return true;
    }

    // Is this version of android capable of background scanning at all?
    public boolean getWifiBgScanCapable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }

        return true;
    }

    public boolean getWifiAlwaysScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            LogAlias.d("smarter", "getwifiscanning: " + wifiManager.isScanAlwaysAvailable());
            return wifiManager.isScanAlwaysAvailable();
        }

        LogAlias.d("smarter", "we can't support wifi always scanning");
        return false;
    }

    public void setPauseAddNewNetwork(boolean v) {
        pausedNewNetwork = v;
        configureWifiState();
    }

    public boolean getPauseAddNewNetwork() {
        return pausedNewNetwork;
    }

    public ArrayList<SmarterTimeRange> getTimeRangeList() {
        return dbSource.getTimeRangeList();
    }

    public void deleteTimeRange(SmarterTimeRange r) {
        dbSource.deleteTimeRange(r);

        configureTimerangeState();
    }

    public long updateTimeRange(SmarterTimeRange r) {
        long ud =  dbSource.updateTimeRange(r);

        LogAlias.d("smarter", "saved time range to " + ud);

        configureTimerangeState();

        return ud;
    }

    public long updateTimeRangeEnabled(SmarterTimeRange r) {
        long ud = dbSource.updateTimeRangeEnabled(r);

        configureTimerangeState();

        return ud;
    }

    private Runnable towerCleanupTask = new Runnable() {
        @Override
        public void run() {
            if (performTowerPurges && dbSource != null && getWifiState() == WifiState.WIFI_ON) {
                SmarterSSID ssid = getCurrentSsid();

                LogAlias.d("smarter", "looking to see if we should purge old towers...");
                dbSource.deleteSsidTowerLastTime(ssid, purgeTowerHours * 60 * 60);

            }

            // every 10 minutes
            timerHandler.postDelayed(this, 1000 * 60 * 10);
        }
    };

    public boolean checkForPermissions() {
        boolean newPermission = false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            newPermission = false;
        } else {
            newPermission = true;
        }

        // Toggle listeners if we need to because we've gained/lost permissions
        if (newPermission && !sufficientPermissions) {
            sufficientPermissions = true;
            configurePermissionListeners();
        }

        if (!newPermission && sufficientPermissions) {
            sufficientPermissions = false;
            removePermissionListeners();
        }

        return sufficientPermissions;
    }

    public void handleWifiScan() {
        LogAlias.d("smarter", "got wifi scan event");
        List<ScanResult> sr = wifiManager.getScanResults();
        worldState.setScanResults(sr);
        configureWifiState();
    }

    public void pingOnWakeup() {
        LogAlias.d("smarter", "pinging on wakeup");

        handleWifiScan();

        if (useWifiScan && getWifiBgScanCapable())
            wifiManager.startScan();

        handleCellLocation(null);

        configureWifiState();

    }
}
