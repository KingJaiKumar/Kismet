package net.kismetwireless.android.smarterwifimanager

import android.Manifest
import android.app.*
import android.arch.lifecycle.*
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
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
import kotlinx.coroutines.experimental.async
import net.kismetwireless.android.smarterwifimanager.Database.*

class SWM2Service : Service(), LifecycleOwner {
    private val binder = LocalBinder()

    companion object {
        private lateinit var database: SWM2Database
        private lateinit var logDao: Swm2LogDao
        private lateinit var networkDao : SWM2NetworkDao
        private lateinit var apDao : SWM2AccessPointDao
        private lateinit var towerDao : SWM2TowerDao

        private var started = false

        private lateinit var lifecycleRegistry : LifecycleRegistry

        private var stateBus = MutableLiveData<Boolean>()

        private var shutdownScheduled : Boolean = false

        private lateinit var notificationManager: NotificationManager
        private val notificationChannelId = "net.kismetwireless.android.swm2"
        private val notificationId = 101

        private lateinit var wifiManager: WifiManager
        private lateinit var telephonyManager : TelephonyManager
        private lateinit var connectivityManager: ConnectivityManager
        private lateinit var alarmManager: AlarmManager
    }

    private val phoneListener = SWMPhoneStateListener()
    private val wifiReceiver = SWMWifiReceiver()
    private val wifiScanReceiver = SWMScanReceiver()

    private val connecivityReceiver = SWMConnectionReceiver()
    private val connectivityCallback = SWMConnectivityCallback()

    fun isNetworkWifi() : Boolean =
            connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI

    fun isNetworkConnected() : Boolean =
            connectivityManager.activeNetworkInfo?.isConnected == true

    fun isOnWifi() : Boolean =
            isNetworkConnected() && isNetworkWifi()

    fun commonNeighborTowers() : MutableList<SWM2CommonTelephony> {
        val commonList: MutableList<SWM2CommonTelephony> = arrayListOf()

        val permission =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)

        if (permission == PackageManager.PERMISSION_GRANTED) {
            val locations = telephonyManager.allCellInfo

            if (locations != null && locations.isNotEmpty()) {
                for (ci in locations) {
                    val swmci = SWM2CommonTelephony(ci)

                    if (swmci.isValid()) {
                        commonList.add(swmci)
                    }
                }
            } else {
                val swmci = SWM2CommonTelephony(telephonyManager.cellLocation)

                if (swmci.isValid()) {
                    commonList.add(swmci)
                }

            }
        }

        return commonList
    }

    fun wifiState() : Int =
            wifiManager.wifiState

    fun isWifiEnabled() : Boolean =
            when (wifiState()) {
                WifiManager.WIFI_STATE_ENABLED -> true
                WifiManager.WIFI_STATE_ENABLING -> true
                WifiManager.WIFI_STATE_DISABLED -> false
                WifiManager.WIFI_STATE_DISABLING -> false
                else -> false
            }

    fun wifiNetwork() : WifiInfo =
            wifiManager.connectionInfo

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
        logDao = database.logDao()
        networkDao = database.networkDao()
        apDao = database.apDao()
        towerDao = database.towerDao()

        dbLog("Service created")

        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.markState(Lifecycle.State.STARTED)

        wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        telephonyManager =
                applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager =
                applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        alarmManager =
                applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager


        notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set a listener on the livedata for when the state changes
        stateBus.observe(this,
                Observer { event ->
                    if (event != null)
                        handleStateChange()
                })

        telephonyManager.listen(phoneListener,
                PhoneStateListener.LISTEN_CELL_LOCATION + PhoneStateListener.LISTEN_CELL_INFO)

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

        scheduleTick()

    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(wifiReceiver)
        unregisterReceiver(wifiScanReceiver)

        cancelShutdown()
        cancelTick()

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
        return database
    }

    fun provideStateBus() : MutableLiveData<Boolean> {
        return stateBus
    }

    fun provideState() : Boolean? {
        return true
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
            val retId = logDao.insertLog(Swm2LogEntry(msg = text))

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

        val content = "Wifi: " + wifiStateToString(wifiState()) +
                " Connected: " + isNetworkConnected() +
                " onWifi: " + isNetworkWifi()

        var icon : Int = R.drawable.ic_swm2_wifi_off

        if (isNetworkWifi() ?: false)
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
        // Lock so we process state changes serially, sometimes we get a flood of changes
        synchronized(this) {
            // Run this on a coroutine so that the database accesses don't cause problems

            val updateJob = async {
                // If we're connected to wifi, we're in learning mode; Learn any new networks,
                // any new BSSIDs, and associate any new towers with the current network
                if (isOnWifi()) {
                    val wifiInfo = wifiNetwork()

                    // If we have wifi info, learn it
                    if (wifiInfo != null) {
                        Log.d("SWM2", "Considering Wi-Fi data")

                        // Generate a network based on the wifi info if we don't have it already
                        var savedNetwork = networkDao.findNetwork(wifiInfo.ssid)
                        var newNetwork = false

                        if (savedNetwork == null) {
                            savedNetwork = SWM2Network(ssid = wifiInfo.ssid)
                            val id = networkDao.insertNetwork(savedNetwork)

                            if (id == null) {
                                dbLog("Failed to insert network in DB for " + wifiInfo.ssid)
                                return@async
                            } else {
                                savedNetwork.id = id
                                newNetwork = true
                            }
                        } else {
                            savedNetwork.lastTime = System.currentTimeMillis()
                            networkDao.updateNetwork(savedNetwork)
                        }

                        if (newNetwork)
                            dbLog("Learning network " + wifiInfo.ssid)

                        // Learn the BSSID we're associated to
                        var savedAp = apDao.findAccessPoint(bssid = wifiInfo.bssid,
                                networkId = savedNetwork.id)

                        if (savedAp == null) {
                            savedAp = SWM2AccessPoint(bssid = wifiInfo.bssid, network_id = savedNetwork.id)
                            if (apDao.insertAccessPoint(savedAp) != null)
                                dbLog("Associated BSSID " + wifiInfo.bssid + " with " + wifiInfo.ssid)
                        } else {
                            savedAp.lastTime = System.currentTimeMillis()
                            apDao.updateAccessPoint(savedAp)
                        }

                        updateDatabaseTowers(savedNetwork.id, savedNetwork.ssid)
                    }
                } else if (isWifiEnabled() && !isOnWifi()) {
                    // Are we near a tower we know about?
                    if (isNearKnownTower()) {
                        dbLog("~WIFI Wi-Fi enabled without connection, but we're near a known tower, leaving Wi-Fi turned on.")
                        return@async
                    }

                    // Otherwise, start the countdown for turning off wifi; we do this by making a
                    // job that waits for 15 seconds then sees if we still need to shut down.
                    //
                    // If we're already running a shutdown job, do nothing
                    if (!shutdownScheduled)
                        scheduleShutdown()
                } else if (!isWifiEnabled()) {
                    // We're not near a wifi network, should we turn it on?
                    val nearTower = findNearKnownTower()

                    if (nearTower.isValid()) {
                        dbLog("+WIFI - Enabling Wi-Fi, near known tower " + nearTower.toString())
                        wifiManager.setWifiEnabled(true)
                    }
                }
            }

            // Fire the notification
            updateNotification()
        }
    }

    fun isNearKnownTower() : Boolean {
        if (findNearKnownTower().isValid())
            return true

        return false
    }

    fun findNearKnownTower() : SWM2CommonTelephony {
        for (tower in commonNeighborTowers()) {
            if (towerDao!!.findCellTowers(tower.toString()).isNotEmpty()) {
                return tower
            }
        }

        return SWM2CommonTelephony()
    }

    fun updateDatabaseTowers(networkId : Long, networkSsid : String) {
        for (tower in commonNeighborTowers()) {
            var logTower = towerDao.findCellTowerAssociation(tower.toString(), networkId)

            if (logTower == null) {
                logTower = SWM2CellTower(towerString = tower.toString(),
                        network_id = networkId)

                if (towerDao.insertTower(logTower) != null)
                    dbLog("Associated tower " + tower.toString() + " with network " +
                            networkSsid);
            } else {
                logTower.lastTime = System.currentTimeMillis()
                towerDao.updateTower(logTower)
            }
        }
    }

    fun scheduleShutdown() {
        val wakeTime = System.currentTimeMillis() + (15 * 1000)
        val intent = Intent(this, SWM2TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent)
    }

    fun cancelShutdown() {
        val intent = Intent(this, SWM2TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
        alarmManager.cancel(pendingIntent)

        synchronized(this) {
            shutdownScheduled = false
        }
    }

    fun scheduleTick() {
        val wakeTime = System.currentTimeMillis() + (30 * 1000)
        val intent = Intent(this, SWM2CheckReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntent)

    }

    fun cancelTick() {
        val intent = Intent(this, SWM2CheckReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
        alarmManager.cancel(pendingIntent)
    }

    fun attemptWifiShutdown() {
        synchronized(this) {
            if (isOnWifi() || isNearKnownTower()) {
                dbLog("~WIFI - Shutdown scheduled, but we're near a known tower or on Wi-Fi now")
                return@synchronized
            }

            dbLog("-WIFI - Shutdown scheduled, turning off Wi-Fi adapter")
            wifiManager.setWifiEnabled(false)

            shutdownScheduled = false
        }
    }

    fun serviceTick() {
        dbLog("Kicking forced check")
        handleStateChange()

        scheduleTick()
    }

    // Utility receiver classes attached to broadcast receivers and state
    // callbacks w/in wifi, telephony, and connectivity managers

    inner class SWMPhoneStateListener : PhoneStateListener() {
        override fun onCellLocationChanged(location: CellLocation?) {
            super.onCellLocationChanged(location)

            // Map this to the generic update function that uses CellInfo or CellLocation
            Log.d("SWM2", "onCellLocationChanged")

            stateBus.postValue(true)
        }

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            super.onCellInfoChanged(cellInfo)

            Log.d("SWM2", "onCellInfoChanged: " + cellInfo?.size)

            stateBus.postValue(true)
        }
    }

    inner class SWMWifiReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stateBus.postValue(true)
        }
    }

    inner class SWMScanReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stateBus.postValue(true)
        }
    }

    inner class SWMConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stateBus.postValue(true)
        }
    }

    inner class SWMConnectivityCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            super.onAvailable(network)
            stateBus.postValue(true)
        }

        override fun onLinkPropertiesChanged(network: Network?, linkProperties: LinkProperties?) {
            super.onLinkPropertiesChanged(network, linkProperties)
            stateBus.postValue(true)
        }

        override fun onLost(network: Network?) {
            super.onLost(network)
            stateBus.postValue(true)
        }

        override fun onUnavailable() {
            super.onUnavailable()
            stateBus.postValue(true)
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

class SWM2IntentServiceShim : IntentService("SWM2ServiceShim") {
    override fun onHandleIntent(p0: Intent?) {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
                val binder = service as SWM2Service.LocalBinder

                if (p0!!.hasExtra("SHUTDOWN"))
                    binder.getService().attemptWifiShutdown()

                else if (p0!!.hasExtra("TICK"))
                    binder.getService().serviceTick()

                unbindService(this)
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                //
            }
        }
        val serviceIntent = Intent(this, SWM2Service::class.java)
        this.startService(serviceIntent)
        this.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}

class SWM2TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, p1: Intent?) {
        val intent = Intent(context, SWM2IntentServiceShim::class.java)
        intent.putExtra("SHUTDOWN", true)
        context?.startService(intent)
    }
}

class SWM2CheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, p1: Intent?) {
        val intent = Intent(context, SWM2IntentServiceShim::class.java)
        intent.putExtra("TICK", true)
        context?.startService(intent)
    }
}

