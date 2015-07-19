package net.kismetwireless.android.smarterwifimanager.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import javax.inject.Inject;

/**
 * Created by dragorn on 7/18/15.
 */
public class SmarterActivity extends AppCompatActivity {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Inject
    Bus eventBus;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SmarterApplication.get(this).inject(this);

        LogAlias.d("smarter", "smarter activity injected service " + serviceBinder);
    }
}
