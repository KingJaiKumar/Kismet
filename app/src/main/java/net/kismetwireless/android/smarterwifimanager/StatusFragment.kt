package net.kismetwireless.android.smarterwifimanager

import android.arch.lifecycle.Observer
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_status.*


/**
 * A simple [Fragment] subclass.
 * Use the [StatusFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class StatusFragment : Fragment() {
    private var mainActivity : MainActivity? = null
    private var mainService : SWM2Service? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivity = activity as MainActivity
        mainService = mainActivity?.myService
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
    }

    private fun updateView(state : SWM2Service.SWM2State?) {
        wifienabled.setText(mainService?.wifiStateToString(state?.wifiState!!))
        networkconnected.setText(state?.isNetworkConnected().toString())
        networkwifi.setText(state?.isNetworkWifi().toString())

        if (state?.lastWifiNetwork == null)
            wifidetails.setText("n/a")
        else
            wifidetails.setText(state?.lastWifiNetwork?.bssid + " " + state?.lastWifiNetwork?.ssid)

        if (state?.lastCellLocation == null)
            lasttower.setText("n/a")
        else
            lasttower.setText(state?.lastCellLocation.toString())
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wifienabled.setText("n/a")
        networkconnected.setText("n/a")
        networkwifi.setText("n/a")
        lasttower.setText("n/a")

        mainService!!.provideStateBus().observe(this,
                Observer { event ->
                    updateView(event)
                })

        updateView(mainService!!.provideState())
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment StatusFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
                StatusFragment().apply { }
    }
}
