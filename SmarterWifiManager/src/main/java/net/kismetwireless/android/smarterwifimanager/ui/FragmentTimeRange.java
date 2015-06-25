package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.models.SmarterTimeRange;
import net.kismetwireless.android.smarterwifimanager.models.TimeCardAdapter;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by dragorn on 10/1/13.
 */
public class FragmentTimeRange extends SmarterFragment {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    private View mainView;
    private FragmentActivity activity;

    private ArrayList<SmarterTimeRange> lastTimeList = new ArrayList<SmarterTimeRange>();

    private RecyclerView recyclerView;
    private TimeCardAdapter timeCardAdapter;
    private RecyclerView.LayoutManager layoutManager;

    private TextView emptyView;

    private FloatingActionButton floatingActionButton;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        context = getActivity().getApplicationContext();

        serviceBinder = new SmarterWifiServiceBinder(context);
        serviceBinder.doBindService();

        if (savedInstanceState != null) {
            lastTimeList = savedInstanceState.getParcelableArrayList("timelist");

            if (recyclerView != null) {
                timeCardAdapter = new TimeCardAdapter(context, (AppCompatActivity) getActivity(), lastTimeList);
                recyclerView.setAdapter(timeCardAdapter);
            }
        } else {
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
                            updateTimeList();
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList("timelist", lastTimeList);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mainView = inflater.inflate(R.layout.fragment_timerange, container, false);

        activity = getActivity();
        context = activity.getApplicationContext();

        recyclerView = (RecyclerView) mainView.findViewById(R.id.recyclerTimeList);
        emptyView = (TextView) mainView.findViewById(R.id.textViewNoTime);

        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);

        recyclerView.setHasFixedSize(false);

        timeCardAdapter = new TimeCardAdapter(context, (AppCompatActivity) getActivity(), lastTimeList);

        recyclerView.setAdapter(timeCardAdapter);

        floatingActionButton = (FloatingActionButton) mainView.findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addTimeRange();
            }
        });

        return mainView;
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save all the current time ranges
        for (SmarterTimeRange str : lastTimeList) {
            str.applyChanges();
            serviceBinder.updateTimeRange(str);
            serviceBinder.updateTimeRangeEnabled(str);
        }
    }

    public void addTimeRange() {
        // Expand new items
        SmarterTimeRange str = new SmarterTimeRange();
        str.setCollapsed(false);
        lastTimeList.add(str);

        if (timeCardAdapter != null)
            timeCardAdapter.notifyDataSetChanged();

        if (emptyView != null && recyclerView != null) {
            if (lastTimeList.size() <= 0) {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }


    private void updateTimeList() {
        ArrayList<SmarterTimeRange> tr = serviceBinder.getTimeRangeList();

        if (tr != null) {
            lastTimeList.clear();
            lastTimeList.addAll(tr);
            timeCardAdapter.notifyDataSetChanged();
        }

        if (lastTimeList.size() <= 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getTitle() {
        return R.string.tab_time;
    }
}
