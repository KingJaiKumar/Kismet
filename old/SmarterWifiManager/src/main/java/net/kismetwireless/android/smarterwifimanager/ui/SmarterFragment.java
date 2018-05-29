package net.kismetwireless.android.smarterwifimanager.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

/**
 * Created by dragorn on 9/23/13.
 */
abstract public class SmarterFragment extends Fragment {
    abstract int getTitle();

    Context context;
    SmarterWifiServiceBinder serviceBinder;
    Bus eventBus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        SmarterActivity activity = (SmarterActivity) getActivity();

        SmarterApplication.get(getActivity()).inject(this);

        context = activity.context;
        serviceBinder = activity.serviceBinder;
        eventBus = activity.eventBus;

        return v;
    }
}
