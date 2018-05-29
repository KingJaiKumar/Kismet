package net.kismetwireless.android.smarterwifimanager

import android.arch.lifecycle.*
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_learned.*
import kotlinx.android.synthetic.main.view_learneditem.view.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import net.kismetwireless.android.smarterwifimanager.Database.SWM2Network

/**
 * A simple [Fragment] subclass.
 * Use the [LearnedFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class LearnedFragment : Fragment() {
    private var mainActivity : MainActivity? = null
    private var mainService : SWM2Service? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivity = activity as MainActivity
        mainService = mainActivity!!.myService
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_learned, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lateinit var netAdapter : NetworkAdapter

        with (learnedrecycler) {
            netAdapter = NetworkAdapter(mainService!!)
            layoutManager = LinearLayoutManager(activity)
            adapter = netAdapter
        }

        var netModel : NetworkViewModel =
                ViewModelProviders.of(activity, NetworkViewModelFactory(mainService!!)).get(NetworkViewModel::class.java)

        netModel.getNetworks().observe(this, Observer<List<SWM2Network>>() {
            if (it != null)
                netAdapter.setNetworks(it)
        })
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment LearnedFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
                LearnedFragment().apply { }
    }

    // This is annoying; we have to make a model for each of the network, tower, and APs to get
    // live updates an trigger updating them all; if there's a better way we'll have to find
    // it in the future
    class NetworkViewModelFactory(private val service: SWM2Service) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NetworkViewModel(service) as T
        }
    }

    class NetworkViewModel(private val service: SWM2Service) : ViewModel() {
        private val networkDao = service.provideDatabase()!!.networkDao()
        private var networkData: LiveData<List<SWM2Network>>? = null

        fun getNetworks() : LiveData<List<SWM2Network>> {
            if (networkData == null) {
                networkData = networkDao.getAllNetworks()
            }
            return networkData!!
        }
    }

    class ApViewModelFactory(private val service: SWM2Service) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ApViewModel(service) as T
        }
    }

    class ApViewModel(private val service: SWM2Service) : ViewModel() {

    }

    class NetworkAdapter(service: SWM2Service) : RecyclerView.Adapter<NetworkAdapter.ViewHolder>() {
        private var networkData : List<SWM2Network>? = null
        private val networkDao = service?.provideDatabase()!!.networkDao()

        fun setNetworks(networks : List<SWM2Network>) {
            networkData = networks

            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
            val layoutInflater = LayoutInflater.from(parent?.context)
            return ViewHolder(layoutInflater.inflate(R.layout.view_learneditem, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            if (networkData != null) {
                val network = networkData?.get(position)

                holder?.nameView?.setText(network?.ssid)

                launch(UI) {
                    val numNetworks = async { networkDao.countBssids(network!!.id) }.await()
                    val numTowers = async { networkDao.countTowers(network!!.id) }.await()

                    holder?.wifiView?.setText(numNetworks.toString())
                    holder?.cellview?.setText(numTowers.toString())
                }
            } else {
                holder?.nameView?.setText("n/a")
                holder?.wifiView?.setText("n/a")
                holder?.cellview?.setText("n/a")
            }
        }

        override fun getItemCount(): Int {
            if (networkData == null)
                return 0

            return networkData!!.size
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView = view.networkname
            val wifiView = view.apcount
            val cellview = view.towercount
        }
    }
}
