package net.kismetwireless.android.smarterwifimanager.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import net.kismetwireless.android.smarterwifimanager.SmarterApplication;

/**
 * Created by dragorn on 10/19/13.
 */
public class ActivityQuickconfig extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SmarterApplication.get(this).inject(this);

        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            QuickConfigDialog qDialog = new QuickConfigDialog();
            qDialog.show(fm, "fragment_edit_name");
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
