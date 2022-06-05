package net.kismetwireless.android.smarterwifimanager

import android.arch.lifecycle.Observer
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_status.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch


/**
 * A simple [Fragment] subclass.
 * Use the [StatusFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class StatusFragment : Fragment() {
    private lateinit var mainActivity : MainActivity
    private lateinit var mainService : SWM2Service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivity = activity as MainActivity
        mainService = mainActivity.myService!!
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

    private fun updateView() {
        wifienabled.setText(mainService.wifiStateToString(mainService.wifiState()))
        networkconnected.setText(mainService.isNetworkConnected().toString())
        networkwifi.setText(mainService.isNetworkWifi().toString())

        launch (UI) {
            if (mainService.wifiNetwork().networkId < 0)
                wifidetails.setText("n/a")
            else
                wifidetails.setText(mainService.wifiNetwork().bssid + " " + mainService.wifiNetwork().ssid)

            var text = mainService.commonNeighborTowers().size.toString() + " towers ["
            for (tower in mainService.commonNeighborTowers()) {
                text += tower.toString()
            }
            text += "]"
            lasttower.setText(text)
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wifienabled.setText("n/a")
        networkconnected.setText("n/a")
        networkwifi.setText("n/a")
        lasttower.setText("n/a")

        mainService!!.provideStateBus().observe(this,
                Observer { event ->
                    updateView()
                })

        updateView()
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
