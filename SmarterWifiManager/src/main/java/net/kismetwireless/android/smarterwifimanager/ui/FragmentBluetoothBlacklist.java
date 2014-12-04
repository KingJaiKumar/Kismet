package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.models.SmarterBluetooth;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/17/13.
 */
public class FragmentBluetoothBlacklist extends SmarterFragment {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    private View mainView;

    private ArrayList<SmarterBluetooth> lastBtList = new ArrayList<SmarterBluetooth>();

    private BluetoothListAdapter listAdapter;
    private ListView lv;
    private TextView emptyView;

    public void updateBluetoothList() {
        ArrayList<SmarterBluetooth> bt = serviceBinder.getBluetoothBlacklist();

        lastBtList.clear();
        lastBtList.addAll(bt);

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();

            if (lastBtList.size() == 0) {
                lv.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                lv.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    private SmarterWifiService.SmarterServiceCallback callback = new SmarterWifiService.SmarterServiceCallback() {
        @Override
        public void bluetoothStateChanged(final SmarterWifiService.BluetoothState state) {
            super.bluetoothStateChanged(state);

            Activity ma = getActivity();

            if (ma != null) {
                ma.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateBluetoothList();
                    }
                });
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        // if (mainView == null)
            mainView = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        lv = (ListView) mainView.findViewById(R.id.bluetoothListView);
        emptyView = (TextView) mainView.findViewById(R.id.textViewNoBluetooth);

        listAdapter = new BluetoothListAdapter(context, R.layout.bluetooth_blacklist_entry, lastBtList);
        lv.setAdapter(listAdapter);

        serviceBinder.addCallback(callback);

        return mainView;
    }

    public class BluetoothListAdapter extends ArrayAdapter<SmarterBluetooth> {
        private int layoutResourceId;

        public BluetoothListAdapter(Context context, int textViewResourceId, ArrayList<SmarterBluetooth> items) {
            super(context, textViewResourceId, items);
            layoutResourceId = textViewResourceId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            try {
                final SmarterBluetooth entry = getItem(position);

                View v = null;

                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                    v = inflater.inflate(layoutResourceId, null);
                } else {
                    v = convertView;
                }

                TextView btView = (TextView) v.findViewById(R.id.btListDevice);
                CheckBox btCb = (CheckBox) v.findViewById(R.id.btListCheck);

                btView.setText(entry.getBtName());
                btCb.setChecked(entry.isBlacklisted());

                btCb.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        CheckBox cb = (CheckBox) view;

                        entry.setBlacklisted(!entry.isBlacklisted());
                        cb.setChecked(entry.isBlacklisted());

                        Log.d("smarter", "listadapter setting " + entry.getBtName() + " to " + entry.isBlacklisted());

                        serviceBinder.setBluetoothBlacklisted(entry, entry.isBlacklisted(), false);
                        listAdapter.notifyDataSetChanged();
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

        if (serviceBinder != null)
            serviceBinder.removeCallback(callback);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceBinder != null)
            serviceBinder.addCallback(callback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int getTitle() {
        return R.string.tab_bluetooth;
    }

}
