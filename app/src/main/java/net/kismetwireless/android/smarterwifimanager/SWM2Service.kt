package net.kismetwireless.android.smarterwifimanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.arch.lifecycle.*
import android.content.*
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
import android.telephony.CellLocation
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
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

        private var lifecycleRegistry : LifecycleRegistry? = null

        private var stateBus = MutableLiveData<SWM2State>()

        private var wifiDisableThread : Thread? = null
        private var wifiDisableThreadRunning : Boolean = false
    }

    private var notificationManager: NotificationManager? = null
    private val notificationChannelId = "net.kismetwireless.android.swm2"
    private val notificationId = 101

    private var wifiManager: WifiManager? = null
    private var telephonyManager : TelephonyManager? = null
    private var connectivityManager: ConnectivityManager? = null

    private val phoneListener = SWMPhoneStateListener()
    private val wifiReceiver = SWMWifiReceiver()
    private val wifiScanReceiver = SWMScanReceiver()

    private val connecivityReceiver = SWMConnectionReceiver()
    private val connectivityCallback = SWMConnectivityCallback()

    private var worldState : SWM2State? = null

    class SWM2State(private val stateBus : MutableLiveData<SWM2State>,
                    private val connectivityManager: ConnectivityManager,
                    private val wifiManager : WifiManager) {

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

        var lastCellLocation : SWM2CommonTower = SWM2CommonTower(null)
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

            stateBus.postValue(this)
        }

        fun isNetworkWifi() : Boolean =
                lastNetworkInfo?.type == ConnectivityManager.TYPE_WIFI

        fun isNetworkConnected() : Boolean =
                lastNetworkInfo?.isConnected == true

        fun updateCellLocation(cellLocation: CellLocation?) {
            val commonTower = SWM2CommonTower(cellLocation)

            if (!commonTower.equals(lastCellLocation)) {
                lastCellLocation = commonTower
                cellLocationTimestamp = System.currentTimeMillis()
                stateBus.postValue(this)
            }
        }

        fun isOnWifi() : Boolean {
            return isNetworkConnected() && isNetworkWifi()
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
        lifecycleRegistry!!.markState(Lifecycle.State.STARTED)

        wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        telephonyManager =
                applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager =
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        worldState = SWM2State(stateBus, connectivityManager!!, wifiManager!!)

        telephonyManager?.listen(phoneListener,
                PhoneStateListener.LISTEN_CELL_LOCATION +
                        PhoneStateListener.LISTEN_CELL_INFO)

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        if (Build.VERSION.SDK_INT >= 24) {
            connectivityManager?.registerDefaultNetworkCallback(connectivityCallback)
        } else {
            registerReceiver(connecivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

        createNotificationChannel(notificationChannelId,
                getString(R.string.app_name), getString(R.string.notification_channel_desc))

        updateNotification()

        // Set a listener on the livedata for when the state changes
        stateBus.observe(this,
                Observer { event ->
                    if (event != null)
                        handleStateChange()
                })
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(wifiReceiver)
        unregisterReceiver(wifiScanReceiver)

        if (Build.VERSION.SDK_INT >= 24) {
            connectivityManager?.unregisterNetworkCallback(connectivityCallback)
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
        return worldState!!
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


            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val intent = Intent(this@SWM2Service, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = PendingIntent.getActivity(this@SWM2Service, 0,
                intent, PendingIntent.FLAG_CANCEL_CURRENT)

        val content = "Wifi: " + wifiStateToString(worldState?.wifiState!!) +
                " Connected: " + worldState?.isNetworkConnected() +
                " onWifi: " + worldState?.isNetworkWifi()

        var icon : Int = R.drawable.ic_swm2_wifi_off

        if (worldState?.isOnWifi() ?: false)
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
            if (worldState!!.isOnWifi() &&
                    worldState!!.lastWifiNetwork != null &&
                    worldState!!.lastCellLocation.isValid()) {

                val wifiInfo = worldState?.lastWifiNetwork

                // If our Wifi info is new, learn it
                if (wifiInfo != null && worldState!!.isWifiFresh()) {
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

                    // Learn about new APs for this network; if the AP is fresh or if we have
                    // a new network
                    if (newNetwork || worldState!!.isWifiNetworkFresh()) {
                        var savedAp = apDao?.findAccessPoint(bssid = wifiInfo.bssid, networkId = savedNetwork.id)

                        if (savedAp == null) {
                            savedAp = SWM2AccessPoint(bssid = wifiInfo.bssid, network_id = savedNetwork.id)
                            if (apDao?.insertAccessPoint(savedAp) != null)
                                dbLog("Associated BSSID " + wifiInfo.bssid + " with " + wifiInfo.ssid)

                        } else {
                            savedAp.lastTime = System.currentTimeMillis()
                            apDao?.updateAccessPoint(savedAp)
                        }
                    }

                    // We've learned anything we need to learn about the current wifi id,
                    // now let's learn about the cell towers nearby, if we can; again, if it's a new
                    // network we instantly learn the current tower, otherwise we update if the info
                    // is fresh
                    val lastTower = worldState?.lastCellLocation

                    if (lastTower != null && (newNetwork || worldState!!.isCellLocationFresh())) {
                        Log.d("SWM2", "Considering tower data")

                        if (lastTower.isValid()) {
                            var savedTower = towerDao?.findCellTowerAssociation(lastTower.bsidcid, lastTower.nidlac, lastTower.sid, savedNetwork.id)
                            if (savedTower == null) {
                                savedTower = SWM2CellTower(bsid_cid = lastTower.bsidcid, nid_lac = lastTower.nidlac, sid = lastTower.sid, network_id = savedNetwork.id)
                                if (towerDao?.insertTower(savedTower) != null)
                                    dbLog("Associated tower " + lastTower.bsidcid.toString() + "." + lastTower.nidlac.toString() + "." + lastTower.sid.toString() + " with network " + wifiInfo.ssid)

                            } else {
                                savedTower.lastTime = System.currentTimeMillis()
                                towerDao?.updateTower(savedTower)
                            }
                        } else {
                            dbLog("Invalid tower, could not process " + lastTower.toString())
                        }
                    }

                }
            } else if (!worldState!!.isOnWifi() && worldState!!.isWifiEnabled()) {
                Log.d("SWM2", "Wifi enabled but we're not on wifi")
                // if we're not on wifi, but wifi is enabled...
                // If we're not near anything we know, we should initiate a shutdown.  If we're
                // near something we know, we should remain on...
                val savedTower =
                        towerDao!!.findCellTowers(worldState!!.lastCellLocation.bsidcid, worldState!!.lastCellLocation.nidlac, worldState!!.lastCellLocation.sid)

                if (savedTower.isEmpty() && worldState!!.lastCellLocation.isValid()) {
                    // If we're not near something we know, start the countdown to powering off.
                    // We do this by making a thread that will sleep for 15 seconds and then shut off
                    // wifi... if we're already waiting to shut down, do nothing.
                    //
                    // The shutdown thread should re-check the current wifi state before disabling to
                    // make sure that we're actually still w/out a reason to turn on
                    synchronized(this) {
                        if (!wifiDisableThreadRunning) {
                            wifiDisableThreadRunning = true

                            wifiDisableThread = Thread({
                                dbLog("-WIFI - No Wi-Fi connection but Wi-Fi enabled; shutting off Wi-Fi in 15 seconds")
                                // First, sleep for 15 seconds
                                Thread.sleep(15 * 1000)

                                // Check if we're on wifi now
                                if (worldState!!.isOnWifi()) {
                                    dbLog("~WIFI - Wi-Fi connection re-established before shutdown timer; cancelling shutdown")
                                    return@Thread
                                }

                                // Check the tower again
                                val retryTower =
                                        towerDao!!.findCellTowers(worldState!!.lastCellLocation.bsidcid, worldState!!.lastCellLocation.nidlac, worldState!!.lastCellLocation.sid)

                                if (retryTower.isEmpty() && worldState!!.lastCellLocation.isValid()) {
                                    dbLog("~WIFI - In range of a known tower; cancelling shutdown")
                                    return@Thread
                                }

                                dbLog("-WIFI - No Wi-Fi connection after 15 seconds; shutting off Wi-Fi")
                                wifiManager?.setWifiEnabled(false)

                                synchronized(this@SWM2Service) {
                                    wifiDisableThreadRunning = false
                                }
                            })
                            wifiDisableThread?.start()
                        }
                    }
                }
            } else if (!worldState!!.isOnWifi()) {
                // We're not on WiFi; look at the tower records

                Log.d("SWM2", "Wifi disabled during event; should we enable it?")

                if (worldState!!.lastCellLocation.isValid()) {
                    val lastTower = worldState!!.lastCellLocation
                    val savedTowers = towerDao?.findCellTowers(lastTower.bsidcid, lastTower.nidlac, lastTower.sid)

                    if (savedTowers!!.isNotEmpty()) {
                        dbLog("+WIFI Near learned tower " + lastTower.toString() + " enabling Wi-Fi")
                        wifiManager?.setWifiEnabled(true)
                    } else {
                        Log.d("SWM2", "No nearby tower is associated with a learned network, " + lastTower.toString())
                    }
                } else {
                    Log.d("SWM2", "Last cell location not valid")
                }
            }

            worldState?.processed()
        }


        // Fire the notification
        updateNotification()
    }

    // Utility receiver classes attached to broadcast receivers and state
    // callbacks w/in wifi, telephony, and connectivity managers

    inner class SWMPhoneStateListener : PhoneStateListener() {
        override fun onCellLocationChanged(location: CellLocation?) {
            super.onCellLocationChanged(location)
            worldState?.updateCellLocation(location)
        }
    }

    inner class SWMWifiReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState?.updateWifiState(
                    intent?.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN))
        }
    }

    inner class SWMScanReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState?.updateWifiScan()
        }
    }

    inner class SWMConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            this@SWM2Service.worldState?.updateConnection()
        }
    }

    inner class SWMConnectivityCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            super.onAvailable(network)
            this@SWM2Service.worldState?.updateConnection()
        }

        override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
            super.onLinkPropertiesChanged(network, linkProperties)
            this@SWM2Service.worldState?.updateConnection()
        }

        override fun onLost(network: Network?) {
            super.onLost(network)
            this@SWM2Service.worldState?.updateConnection()
        }

        override fun onUnavailable() {
            super.onUnavailable()
            this@SWM2Service.worldState?.updateConnection()
        }
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry!!
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

