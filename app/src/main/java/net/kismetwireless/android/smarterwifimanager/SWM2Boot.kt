package net.kismetwireless.android.smarterwifimanager

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.support.v4.content.ContextCompat

class SWM2Boot : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true)) {
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
                    val binder = service as SWM2Service.LocalBinder

                    binder.getService().onStartup()
                }

                override fun onServiceDisconnected(p0: ComponentName?) {
                    //
                }
            }

            val permission =
                    ContextCompat.checkSelfPermission(context!!, Manifest.permission.ACCESS_COARSE_LOCATION)

            if (permission != PackageManager.PERMISSION_GRANTED)
                return

            val serviceIntent = Intent(context, SWM2Service::class.java)
            context?.startService(serviceIntent)
            context?.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
}

