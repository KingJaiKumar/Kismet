package net.kismetwireless.android.smarterwifimanager.ui;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import net.kismetwireless.android.smarterwifimanager.R;

public class ActivitySsidBlacklist extends SmarterActivity {
    FragmentSsidBlacklist ssidFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_singlefragment);

        Toolbar toolBar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            ssidFragment = (FragmentSsidBlacklist) getSupportFragmentManager().findFragmentByTag("ssidfragment");
        } else if (findViewById(R.id.fragment_container) != null) {
            ssidFragment = new FragmentSsidBlacklist();
            ssidFragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, ssidFragment, "ssidfragment").commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.ssid_blacklist, menu);
        return true;
    }
    
}
