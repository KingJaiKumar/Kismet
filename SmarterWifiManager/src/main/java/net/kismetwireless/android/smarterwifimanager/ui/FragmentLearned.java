package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.models.SmarterSSID;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/17/13.
 */
public class FragmentLearned extends SmarterFragment {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    private View mainView;

    private ArrayList<SmarterSSID> lastSsidList = new ArrayList<SmarterSSID>();

    private LearnedSsidListAdapter listAdapter;
    private ListView lv;
    private TextView emptyView;

    private Handler timeHandler = new Handler();

    private void updateTowerList() {
        ArrayList<SmarterSSID> ssids = serviceBinder.getSsidTowerlist();

        if (ssids != null) {
            // Log.d("smarter", "ssid list size" + ssids.size());
            lastSsidList.clear();
            lastSsidList.addAll(ssids);
            listAdapter.notifyDataSetChanged();
        }

        if (lastSsidList.size() <= 0) {
            emptyView.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            lv.setVisibility(View.VISIBLE);
        }
    }

    private Runnable updateTowerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTowerList();

            timeHandler.postDelayed(this, 1000);
        }
    };

    private SmarterWifiService.SmarterServiceCallback serviceCallback = new SmarterWifiService.SmarterServiceCallback() {
        @Override
        public void wifiStateChanged(SmarterSSID ssid, SmarterWifiService.WifiState state,
                                     final SmarterWifiService.WifiState controlstate,
                                     final SmarterWifiService.ControlType type) {
            super.wifiStateChanged(ssid, state, controlstate, type);

            Activity ma = getActivity();

            if (ma == null)
                return;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        // if (mainView == null)
            mainView = inflater.inflate(R.layout.fragment_learnedssid, container, false);

        lv = (ListView) mainView.findViewById(R.id.learnedListView);
        emptyView = (TextView) mainView.findViewById(R.id.textViewNoneLearned);

        listAdapter = new LearnedSsidListAdapter(context, R.layout.ssid_learnlist_entry, lastSsidList);
        lv.setAdapter(listAdapter);

        serviceBinder.addCallback(serviceCallback);

        serviceBinder.doCallAndBindService(new SmarterWifiServiceBinder.BinderCallback() {
            @Override
            public void run(SmarterWifiServiceBinder b) {
                if (!isAdded())
                    return;

                Activity a = getActivity();

                if (a == null)
                    return;

                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTowerRunnable.run();
                    }
                });
            }
        });

        return mainView;
    }

    public class LearnedSsidListAdapter extends ArrayAdapter<SmarterSSID> {
        private int layoutResourceId;

        public LearnedSsidListAdapter(Context context, int textViewResourceId, ArrayList<SmarterSSID> items) {
            super(context, textViewResourceId, items);
            layoutResourceId = textViewResourceId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                final SmarterSSID entry = getItem(position);

                View v = null;

                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                    v = inflater.inflate(layoutResourceId, null);
                } else {
                    v = convertView;
                }

                TextView ssidView = (TextView) v.findViewById(R.id.ssidListSsid);
                TextView towerView = (TextView) v.findViewById(R.id.ssidListTower);
                ImageView trashImage = (ImageView) v.findViewById(R.id.ssidListDelete);

                ssidView.setText(entry.getDisplaySsid());
                towerView.setText(String.format(getString(R.string.learned_number), entry.getNumTowers()));

                trashImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                        builder.setTitle(R.string.delete_dialog_title);
                        builder.setMessage(R.string.delete_learned_message);

                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        });

                        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                serviceBinder.deleteSsidTowerMap(entry);

                                lastSsidList = serviceBinder.getSsidTowerlist();
                                listAdapter.clear();
                                listAdapter.addAll(lastSsidList);
                            }
                        });

                        builder.create().show();

                    }
                });

                return v;
            } catch (Exception ex) {
                Log.e("smarter", "error", ex);
                return null;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        timeHandler.removeCallbacks(updateTowerRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Log.d("smarter", "fragmentlearned onresume");
        updateTowerRunnable.run();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        timeHandler.removeCallbacks(updateTowerRunnable);

        if (serviceBinder != null)
            serviceBinder.doUnbindService();
    }

    @Override
    public int getTitle() {
        return R.string.tab_learned;
    }

}
