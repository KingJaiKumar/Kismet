package net.kismetwireless.android.smarterwifimanager

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_log.*
import kotlinx.android.synthetic.main.view_logitem.view.*
import net.kismetwireless.android.smarterwifimanager.Database.Swm2LogEntry
import java.text.SimpleDateFormat
import java.util.*


/**
 * A simple [Fragment] subclass.
 * Use the [Log.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class LogFragment : Fragment() {
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
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lateinit var logAdapter : LogAdapter

        with (logrecycler) {
            logAdapter = LogAdapter()
            layoutManager = LinearLayoutManager(activity)
            adapter = logAdapter
        }

        var logModel : LogViewModel =
                ViewModelProviders.of(activity, LogViewModelFactory(mainService!!)).get(LogViewModel::class.java)

        logModel.getAllLogs().observe(this, Observer<List<Swm2LogEntry>>() {
            if (it != null)
                logAdapter.setLogs(it)
        })

    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment Log.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
                LogFragment().apply { }
    }

    class LogViewModelFactory(private val service: SWM2Service) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LogViewModel(service) as T
        }
    }
    class LogViewModel(private val service: SWM2Service) : ViewModel() {
        private val logDao = service.provideDatabase()!!.logDao()
        private var logData: LiveData<List<Swm2LogEntry>>? = null

        fun getAllLogs(): LiveData<List<Swm2LogEntry>> {
            if (logData == null)
                logData = logDao.getAllLogsInverse()

            return logData!!
        }
    }

    class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {
        private var logData : List<Swm2LogEntry>? = null

        fun setLogs(logs : List<Swm2LogEntry>) {
            logData = logs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
            val layoutInflater = LayoutInflater.from(parent?.context)
            return ViewHolder(layoutInflater.inflate(R.layout.view_logitem, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            if (logData != null) {
                val log = logData?.get(position)

                val date = Date(log!!.time * 1000)
                val formatter = SimpleDateFormat("MM/dd HH:mm:ss")

                holder?.timeView?.setText(formatter.format(date))
                holder?.textView?.setText(log?.msg)
            } else {
                holder?.timeView?.setText("none")
                holder?.textView?.setText("none")
            }
        }

        override fun getItemCount(): Int {
            if (logData == null)
                return 0

            return logData!!.size
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val timeView = view.logTime
            val textView = view.logText
        }
    }
}

