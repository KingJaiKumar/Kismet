package net.kismetwireless.android.smarterwifimanager.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import net.kismetwireless.android.smarterwifimanager.R;

public class ActivityBluetoothBlacklist extends ActionBarActivity {
    FragmentBluetoothBlacklist bluetoothFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_singlefragment);

        Toolbar toolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            bluetoothFragment = (FragmentBluetoothBlacklist) getSupportFragmentManager().findFragmentByTag("btfragment");
        } else if (findViewById(R.id.fragment_container) != null) {
            bluetoothFragment = new FragmentBluetoothBlacklist();
            bluetoothFragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, bluetoothFragment, "btfragment").commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_blacklist, menu);
        return true;
    }
    
}
