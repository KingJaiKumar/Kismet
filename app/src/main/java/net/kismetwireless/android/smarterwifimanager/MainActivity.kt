package net.kismetwireless.android.smarterwifimanager

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : FragmentActivity() {
    var myService: SWM2Service? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as SWM2Service.LocalBinder
            myService = binder.getService()
            isBound = true

            myService?.dbLog("Main activity bound service")
            Log.d("SWM2", "Main activity thinks it bound the service")

            swapFragment(StatusFragment.newInstance())
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            isBound = false
        }
    }

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_dashboard -> {
                if (isBound) {
                    swapFragment(StatusFragment.newInstance())
                    return@OnNavigationItemSelectedListener true
                }

                return@OnNavigationItemSelectedListener false
            }
            R.id.navigation_home -> {
                if (isBound) {
                    swapFragment(LearnedFragment.newInstance())
                    return@OnNavigationItemSelectedListener true
                }

                return@OnNavigationItemSelectedListener false
            }
            R.id.navigation_log -> {
                if (isBound) {
                    swapFragment(LogFragment.newInstance())
                    return@OnNavigationItemSelectedListener true
                }

                return@OnNavigationItemSelectedListener false
            }
        }
        false
    }

    private val PERM_REQ_CODE_LOCATION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)

        swapFragment(LoadingFragment.newInstance())

        myService?.dbLog("Started main activity")

        // Request permissions and launch the service if we already have them
        if (checkPermissions() == true)
            launchService()
    }

    private fun launchService() {
        val intent = Intent(this, SWM2Service::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(serviceConnection)
    }

    private fun swapFragment(fragment : Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentholder, fragment)
        transaction.commit()
    }

    private fun checkPermissions() : Boolean {
        val permission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            myService?.dbLog("Missing ACCESS_COARSE_LOCATION permission")

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.ACCESS_COARSE_LOCATION)) {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(R.string.permission_explain_location)
                builder.setTitle(R.string.permission_title)
                builder.setPositiveButton(R.string.dialog_ok) {
                    dialog, id ->
                    requestPermissions()
                }

                val dialog = builder.create()
                dialog.show()
            } else {
                requestPermissions()
            }

            // We don't have the permissions, the permissions requester will continue for us
            return false
        }

        // We had the permissions already
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                PERM_REQ_CODE_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_REQ_CODE_LOCATION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    myService?.dbLog("Requested ACCESS_COARSE_LOCATION but was denied")
                } else {
                    myService?.dbLog("Got ACCESS_COARSE_LOCATION")
                    launchService()
                }
            }
        }
    }
}
