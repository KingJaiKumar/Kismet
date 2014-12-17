package net.kismetwireless.android.smarterwifimanager.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;

import javax.inject.Inject;

/**
 * Created by dragorn on 9/24/13.
 */
public class ActivityPrefs extends Activity {
    @Inject
    Bus eventBus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar ab = getActionBar();

        ab.setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new FragmentPrefs()).commit();
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
