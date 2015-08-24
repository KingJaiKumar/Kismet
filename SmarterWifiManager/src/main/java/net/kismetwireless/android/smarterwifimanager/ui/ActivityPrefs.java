package net.kismetwireless.android.smarterwifimanager.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/24/13.
 */
public class ActivityPrefs extends AppCompatActivity {
    @Inject
    Bus eventBus;

    FragmentPrefs prefsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_singlefragment);

        SmarterApplication.get(this).inject(this);

        Toolbar toolBar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolBar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            prefsFragment = (FragmentPrefs) getFragmentManager().findFragmentByTag("prefsfragment");
        } else if (findViewById(R.id.fragment_container) != null) {
            prefsFragment = new FragmentPrefs();
            prefsFragment.setArguments(getIntent().getExtras());

            getFragmentManager().beginTransaction().add(R.id.fragment_container, prefsFragment, "prefsfragment").commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        eventBus.post(new EventPreferencesChanged());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
