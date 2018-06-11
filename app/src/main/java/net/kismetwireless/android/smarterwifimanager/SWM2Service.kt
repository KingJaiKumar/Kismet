package net.kismetwireless.android.smarterwifimanager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.arch.lifecycle.*
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.telephony.CellInfo
import android.telephony.CellLocation
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.experimental.async
import net.kismetwireless.android.smarterwifimanager.Database.*

class SWM2Service : Service(), LifecycleOwner {
    private val binder = LocalBinder()

    companion object {
        private var database: SWM2Database? = null
        private var logDao: Swm2LogDao? = null
        private var networkDao : SWM2NetworkDao? = null
        private var apDao : SWM2AccessPointDao? = null
        private var towerDao : SWM2TowerDao? = null

        private var started = false

        private lateinit var lifecycleRegistry : LifecycleRegistry

        private var stateBus = MutableLiveData<SWM2State>()

        private var wifiDisableThread : Thread? = null
        private var wifiDisableThreadRunning : Boolean = false

        private lateinit var notificationManager: NotificationManager
        private val notificationChannelId = "net.kismetwireless.android.swm2"
        private val notificationId = 101

        private lateinit var wifiManager: WifiManager
        private lateinit var telephonyManager : TelephonyManager
        private lateinit var connectivityManager: ConnectivityManager

        private var worker : PeriodicWorkRequest? = null

    }

    private lateinit var worldState : SWM2State

    private val phoneListener = SWMPhoneStateListener()
    private val wifiReceiver = SWMWifiReceiver()
    private val wifiScanReceiver = SWMScanReceiver()

    private val connecivityReceiver = SWMConnectionReceiver()
    private val connectivityCallback = SWMConnectivityCallback()


    class SWM2State(private val context : Context,
                    private val stateBus : MutableLiveData<SWM2State>,
                    private val connectivityManager: ConnectivityManager,
                    private val wifiManager : WifiManager,
                    private val telephonyManager: TelephonyManager) {

        private var initialized = false

        var lastProcessedTime : Long = 0
            private set

        fun processed() {
            lastProcessedTime = System.currentTimeMillis()
        }

        var wifiState : Int? = WifiManager.WIFI_STATE_UNKNOWN
            private set

        private var wifiStateTimestamp : Long = 0

        fun isWifiStateFresh() : Boolean =
                wifiStateTimestamp > lastProcessedTime

        var oldWifiState : Int? = WifiManager.WIFI_STATE_UNKNOWN
            private set

        var lastNetworkInfo : NetworkInfo? = null
            private set

        private var activeNetworkTimestamp : Long = 0

        fun isNetworkInfoFresh() : Boolean =
                activeNetworkTimestamp > lastProcessedTime

        fun isWifiFresh() : Boolean =
                isWifiStateFresh() || isWifiNetworkFresh()

        var cellLocations : List<SWM2CommonTelephony> = listOf()
            private set

        private var cellLocationTimestamp : Long = 0

        fun isCellLocationFresh() : Boolean =
                cellLocationTimestamp > lastProcessedTime

        var lastWifiNetwork : WifiInfo? = null
            private set

        private var wifiNetworkTimestamp : Long = 0

        fun isWifiNetworkFresh() : Boolean =
                wifiNetworkTimestamp > lastProcessedTime

        var lastWifiScan : List<ScanResult> = listOf()
            private set

        private var wifiScanTimestamp : Long = 0

        fun isWifiScanFresh() : Boolean =
                wifiScanTimestamp > lastProcessedTime

        // Set the Wifi state
        fun updateWifiState(newstate : Int? = WifiManager.WIFI_STATE_UNKNOWN) {
            oldWifiState = wifiState
            wifiState = newstate

            if (wifiState == WifiManager.WIFI_STATE_ENABLED)
                lastWifiNetwork = wifiManager.connectionInfo
            else
                lastWifiNetwork = null

            wifiNetworkTimestamp = System.currentTimeMillis()

            if (wifiState != oldWifiState)
                wifiStateTimestamp = System.currentTimeMillis()

            if (initialized)
                stateBus.postValue(this)
        }

        fun isWifiEnabled() : Boolean =
            when (wifiState) {
                WifiManager.WIFI_STATE_ENABLED -> true
                WifiManager.WIFI_STATE_ENABLING -> true
                WifiManager.WIFI_STATE_DISABLED -> false
                WifiManager.WIFI_STATE_DISABLING -> false
                else -> false
            }

        // Trigger that we have new wifi neighbors
        fun updateWifiScan() {
            lastWifiScan = wifiManager.scanResults

            wifiScanTimestamp = System.currentTimeMillis()

            if (initialized)
                stateBus.postValue(this)
        }

        // Trigger updating the active network info
        fun updateConnection() {
            lastNetworkInfo = connectivityManager.activeNetworkInfo

            if (isNetworkWifi() and isNetworkConnected()) {
                val newWifiNetwork = wifiManager.connectionInfo
                if (lastWifiNetwork == null || lastWifiNetwork?.bssid != lastWifiNetwork?.bssid ||
                        newWifiNetwork?.ssid != lastWifiNetwork?.ssid) {
                    lastWifiNetwork = newWifiNetwork
                    wifiNetworkTimestamp = System.currentTimeMillis()
                }
            } else {
                if (lastWifiNetwork != null) {
                    lastWifiNetwork = null
                    wifiNetworkTimestamp = System.currentTimeMillis()
                }
            }

            activeNetworkTimestamp = System.currentTimeMillis()

            if (initialized)
                stateBus.postValue(this)
        }

        fun isNetworkWifi() : Boolean =
                lastNetworkInfo?.type == ConnectivityManager.TYPE_WIFI

        fun isNetworkConnected() : Boolean =
                lastNetworkInfo?.isConnected == true

        // Utilize either the modern cellinfo list or the legacy celLLocation method to try
        // to get an updated location
        fun updateCellLocations() {
            val permission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

            if (permission == PackageManager.PERMISSION_GRANTED) {
                val locations = telephonyManager.allCellInfo

                Log.d("SWM2", "updateLocations - " + locations?.size)

                val commonList: MutableList<SWM2CommonTelephony> = arrayListOf()

                if (locations != null && locations.isNotEmpty()) {
                    for (ci in locations) {
                        val swmci = SWM2CommonTelephony(ci)

                        if (swmci.isValid()) {
                            Log.d("SWM2", "Got valid swmci: " + swmci.toString())
                            commonList.add(swmci)
                        }
                    }
                } else {
                    val swmci = SWM2CommonTelephony(telephonyManager.cellLocation)

                    if (swmci.isValid()) {
                        commonList.add(swmci)
                    }

                }
                Log.d("SWM2", "Cell info: " + commonList.size)

                // Different size?  Immediately different
                if (commonList.size != cellLocations.size) {
                    cellLocations = commonList
                    cellLocationTimestamp = System.currentTimeMillis()

                    if (initialized)
                        stateBus.postValue(this)

                    return
                }

                // This is a little dumb but we should never have more than a small
                // handful of nearby towers
                for (new_tower in commonList) {
                    var present = false

                    for (old_tower in cellLocations) {
                        if (new_tower.equals(old_tower)) {
                            present = true
                            break
                        }
                    }

                    // If we didn't find this tower in our old list, replace the list, push the update,
                    // and get out
                    if (!present) {
                        cellLocations = commonList
                        cellLocationTimestamp = System.currentTimeMillis()

                        if (initialized)
                            stateBus.postValue(this)

                        return
                    }
                }
            }
        }

        fun isOnWifi() : Boolean {
            return isNetworkConnected() && isNetworkWifi()
        }

        init {
            updateWifiState(wifiManager.wifiState)

            cellLocations = arrayListOf()

            updateCellLocations()

            initialized = true

            stateBus.postValue(this)
        }

    }

    override fun onBind(intent : Intent): IBinder? {
        Log.d("SWM2", "SWM2StateService Binding")
        return binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("SWM2", "SWM2StateService onStartCommand")
        // return Service.START_STICKY
        return Service.START_REDELIVER_INTENT
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("SWM2", "SWM2StateService onCreate")

        database = provideSWM2Database(this)
        logDao = database?.logDao()
        networkDao = database?.networkDao()
        apDao = database?.apDao()
        towerDao = database?.towerDao()

        dbLog("Service created")

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.markState(Lifecycle.State.STARTED)

        wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        telephonyManager =
                applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager =
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


        notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set a listener on the livedata for when the state changes
        stateBus.observe(this,
                Observer { event ->
                    if (event != null)
                        handleStateChange()
                })

        worldState = SWM2State(this, stateBus, connectivityManager,
                wifiManager, telephonyManager)

        telephonyManager.listen(phoneListener,
                PhoneStateListener.LISTEN_CELL_LOCATION + PhoneStateListener.LISTEN_CELL_INFO)

        /*
        val permission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            if (telephonyManager?.allCellInfo != null) {
                dbLog("Using CELL_INFO to get neighboring towers")
                telephonyManager?.listen(phoneListener,
                        PhoneStateListener.LISTEN_CELL_INFO)
            } else {
                dbLog("Using old CELL_LOCATION to get connected tower")
                telephonyManager?.listen(phoneListener,
                        PhoneStateListener.LISTEN_CELL_LOCATION)
            }

        }
        */

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        if (Build.VERSION.SDK_INT >= 24) {
            connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
        } else {
            registerReceiver(connecivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

        createNotificationChannel(notificationChannelId,
                getString(R.string.app_name), getString(R.string.notification_channel_desc))

        updateNotification()

    }

    override fun onDestroy() {
        super.onDestroy()

        if (worker != null) {
            WorkManager.getInstance().cancelAllWorkByTag("TOWERCHECK")
        }

        unregisterReceiver(wifiReceiver)
        unregisterReceiver(wifiScanReceiver)

        if (Build.VERSION.SDK_INT >= 24) {
            connectivityManager.unregisterNetworkCallback(connectivityCallback)
        } else {
            unregisterReceiver(connecivityReceiver)
        }

        /*
        // Try to restart ourselves as we go
        dbLog("SWM2 service shutting down, preparing to re-kick")
        val restartIntent = Intent("net.kismetwireless.android.swm2.RestartService")
        sendBroadcast(restartIntent)
        */
    }

    fun provideDatabase() : SWM2Database? {
        return database!!
    }

    fun provideStateBus() : MutableLiveData<SWM2State> {
        return stateBus
    }

    fun provideState() : SWM2State? {
        return worldState
    }

    inner class LocalBinder : Binder() {
        fun getService() : SWM2Service {
            return this@SWM2Service
        }
    }

    // Basic bootup tasks, establish the state, etc
    fun onStartup() {
        if (started)
            return

        started = true
        dbLog("Started backround service")
    }

    fun dbLog(text : String) {
        // Run DB entries as threads because it's a blocking operation
        val job = async {
            val retId = logDao?.insertLog(Swm2LogEntry(msg = text))

            Log.d("SWM2", "DB log " + retId.toString() + ": " + text)
        }

    }

    private fun createNotificationChannel(id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_MIN)

            channel.description = description
            channel.enableLights(false)
            channel.enableVibration(false)
            channel.lockscreenVisibility = 0
            channel.setShowBadge(false)


            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val intent = Intent(this@SWM2Service, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = PendingIntent.getActivity(this@SWM2Service, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT)

        val content = "Wifi: " + wifiStateToString(worldState.wifiState!!) +
                " Connected: " + worldState.isNetworkConnected() +
                " onWifi: " + worldState.isNetworkWifi()

        var icon : Int = R.drawable.ic_swm2_wifi_off

        if (worldState.isOnWifi() ?: false)
            icon = R.drawable.ic_swm2_wifi

        val notification = NotificationCompat.Builder(this@SWM2Service, notificationChannelId)
                .setChannelId(notificationChannelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(icon)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()

        startForeground(notificationId, notification)
    }

    fun wifiStateToString(wifiState : Int) : String =
            when (wifiState) {
                WifiManager.WIFI_STATE_UNKNOWN -> "Unknown"
                WifiManager.WIFI_STATE_DISABLED -> "Disabled"
                WifiManager.WIFI_STATE_DISABLING -> "Disabling"
                WifiManager.WIFI_STATE_ENABLED -> "Enabled"
                WifiManager.WIFI_STATE_ENABLING -> "Enabling"
                else -> "Unknown"
            }

    private fun handleStateChange() {
        if (worldState == null)
            return

        /*
        if (0 == 1) {
            // Verbose state logging, for now
            var content = "State Changed: Connected(" + worldState?.isNetworkConnected() + ") " +
                    "OnWifi(" + worldState?.isNetworkWifi() + ") "
            "WifiEnabled(" + wifiStateToString(worldState?.wifiState!!) + ")"

            if (worldState?.lastNetworkInfo != null)
                content +=
                        " ConnectionInfo(" + worldState?.lastNetworkInfo?.toString() + ")"

            if (worldState?.lastCellLocation != null)
                content +=
                        " CellLocation(" + worldState?.lastCellLocation?.toString() + ")"

            dbLog(content)
        }
        */

        // Run this on a coroutine so that the database accesses don't cause problems
        val updateJob = async {
            // If we're connected to wifi, we're in learning mode; Learn any new networks,
            // any new BSSIDs, and associate any new towers with the current network
            if (worldState.isOnWifi() &&
                    worldState.lastWifiNetwork != null &&
                    worldState.cellLocations.isNotEmpty()) {

                val wifiInfo = worldState.lastWifiNetwork

                // If we have wifi info, learn it
                if (wifiInfo != null) {
                    Log.d("SWM2", "Considering Wi-Fi data")

                    // Learn brand new networks, or update networks we already know about
                    var savedNetwork = networkDao?.findNetwork(wifiInfo.ssid)
                    var newNetwork = false

                    if (savedNetwork == null) {
                        savedNetwork = SWM2Network(ssid = wifiInfo.ssid)
                        val id = networkDao?.insertNetwork(savedNetwork)

                        if (id == null) {
                            dbLog("Failed to insert network in DB for " + wifiInfo.ssid)
                            return@async
                        } else {
                            savedNetwork.id = id
                            newNetwork = true
                        }
                    } else {
                        savedNetwork.lastTime = System.currentTimeMillis()
                        networkDao?.updateNetwork(savedNetwork)
                    }

                    if (newNetwork)
                        dbLog("Learning network " + wifiInfo.ssid)

                    // Learn the BSSID or update
                    var savedAp = apDao?.findAccessPoint(bssid = wifiInfo.bssid, networkId = savedNetwork.id)

                    if (savedAp == null) {
                        savedAp = SWM2AccessPoint(bssid = wifiInfo.bssid, network_id = savedNetwork.id)
                        if (apDao?.insertAccessPoint(savedAp) != null)
                            dbLog("Associated BSSID " + wifiInfo.bssid + " with " + wifiInfo.ssid)

                    } else {
                        savedAp.lastTime = System.currentTimeMillis()
                        apDao?.updateAccessPoint(savedAp)
                    }

                    // Update the towers for this network
                    if (worldState.cellLocations.isNotEmpty()) {
                        Log.d("SWM2", "Considering tower data")

                        for (tower in worldState.cellLocations) {
                            var savedTower = towerDao?.findCellTowerAssociation(tower.toString(),
                                    savedNetwork.id)

                            if (savedTower == null) {
                                savedTower =
                                        SWM2CellTower(towerString =  tower.toString(),
                                                network_id = savedNetwork.id)
                                if (towerDao?.insertTower(savedTower) != null)
                                    dbLog("Associated tower " + tower.toString() +
                                            " with network " + wifiInfo.ssid)
                            } else {
                                savedTower.lastTime = System.currentTimeMillis()
                                towerDao?.updateTower(savedTower)
                            }
                        }
                    }

                }
            } else if (!worldState.isOnWifi() && worldState.isWifiEnabled()) {
                Log.d("SWM2", "Wifi enabled but we're not on wifi")
                // if we're not on wifi, but wifi is enabled...
                // If we're not near anything we know, we should initiate a shutdown.  If we're
                // near something we know, we should remain on...
                var nearTower = false

                for (tower in worldState.cellLocations) {
                    if (towerDao!!.findCellTowers(tower.toString()).isNotEmpty()) {
                        nearTower = true
                        break
                    }
                }

                if (!nearTower) {
                    // If we're not near something we know, start the countdown to powering off.
                    // We do this by making a thread that will sleep for 15 seconds and then shut off
                    // wifi... if we're already waiting to shut down, do nothing.
                    //
                    // The shutdown thread should re-check the current wifi state before disabling to
                    // make sure that we're actually still w/out a reason to turn on
                    synchronized(this) {
                        if (!wifiDisableThreadRunning) {
                            wifiDisableThreadRunning = true

                            net.kismetwireless.android.smarterwifimanager.SWM2Service.Companion.wifiDisableThread = Thread({
                                dbLog("-WIFI - No Wi-Fi connection but Wi-Fi enabled; shutting off Wi-Fi in 15 seconds")
                                // First, sleep for 15 seconds
                                Thread.sleep(15 * 1000)

                                // Check if we're on wifi now
                                if (worldState.isOnWifi()) {
                                    dbLog("~WIFI - Wi-Fi connection re-established before shutdown timer; cancelling shutdown")
                                    return@Thread
                                }

                                // Check the tower again
                                var nearTower = false

                                for (tower in worldState.cellLocations) {
                                    if (towerDao!!.findCellTowers(tower.toString()).isNotEmpty()) {
                                        nearTower = true
                                        break
                                    }
                                }

                                if (nearTower) {
                                    dbLog("~WIFI - In range of a known tower; cancelling shutdown")
                                    return@Thread
                                }

                                dbLog("-WIFI - No Wi-Fi connection after 15 seconds; shutting off Wi-Fi")
                                wifiManager.setWifiEnabled(false)

                                synchronized(this@SWM2Service) {
                                    wifiDisableThreadRunning = false
                                }
                            })
                            wifiDisableThread?.start()
                        }
                    }
                }
            } else if (!worldState.isOnWifi()) {
                // We're not on WiFi; look at the tower records

                Log.d("SWM2", "Wifi disabled during event; should we enable it?")

                var nearTower = false
                var lastTower = SWM2CommonTelephony()

                for (tower in worldState.cellLocations) {
                    if (towerDao!!.findCellTowers(tower.toString()).isNotEmpty()) {
                        nearTower = true
                        lastTower = tower
                        break
                    }
                }

                if (nearTower) {
                    dbLog("+WIFI Near learned tower " + lastTower?.toString() + " enabling Wi-Fi")
                    wifiManager.setWifiEnabled(true)
                } else {
                    Log.d("SWM2", "Last cell location not valid")
                }
            }

            worldState.processed()
        }


        // Fire the notification
        updateNotification()
    }

    // Utility receiver classes attached to broadcast receivers and state
    // callbacks w/in wifi, telephony, and connectivity managers

    inner class SWMPhoneStateListener : PhoneStateListener() {
        override fun onCellLocationChanged(location: CellLocation?) {
            super.onCellLocationChanged(location)

            // Map this to the generic update function that uses CellInfo or CellLocation
            Log.d("SWM2", "onCellLocationChanged")
            worldState.updateCellLocations()

        }

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            super.onCellInfoChanged(cellInfo)

            Log.d("SWM2", "onCellInfoChanged: " + cellInfo?.size)

            worldState.updateCellLocations()
        }
    }

    inner class SWMWifiReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState.updateWifiState(
                    intent?.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN))
        }
    }

    inner class SWMScanReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState.updateWifiScan()
        }
    }

    inner class SWMConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState.updateConnection()
        }
    }

    inner class SWMConnectivityCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            super.onAvailable(network)
            this@SWM2Service.worldState.updateConnection()
        }

        override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
            super.onLinkPropertiesChanged(network, linkProperties)
            this@SWM2Service.worldState.updateConnection()
        }

        override fun onLost(network: Network?) {
            super.onLost(network)
            this@SWM2Service.worldState.updateConnection()
        }

        override fun onUnavailable() {
            super.onUnavailable()
            this@SWM2Service.worldState.updateConnection()
        }
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }
}

// Receiver to re-launch the service if the service gets killed; we fire an intent
// as it dies that hits this receiver and reloads it
class SWM2ServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
                val binder = service as SWM2Service.LocalBinder

                binder.getService().onStartup()
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                //
            }
        }
        val serviceIntent = Intent(context, SWM2Service::class.java)
        context?.startService(serviceIntent)
        context?.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}

