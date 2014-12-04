package net.kismetwireless.android.smarterwifimanager.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

/**
 * Created by dragorn on 9/23/13.
 */
abstract public class SmarterFragment extends Fragment {
    abstract int getTitle();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        SmarterApplication.get(getActivity()).inject(this);

        return v;
    }
}
