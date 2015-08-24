package net.kismetwireless.android.smarterwifimanager.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;


// Main icon color shifts
// 00e8d5    b8b8b8    a40000

public class MainActivity extends SmarterActivity {
    private static int PREFS_REQ = 1;

    private Toolbar toolBar;

    private FragmentMain mainFragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SmarterApplication.get(this).inject(this);

        setContentView(R.layout.activity_main);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean("everbeenrun", true);
        e.commit();

        eventBus.post(new EventPreferencesChanged());

        // drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        // navigationView = (NavigationView) findViewById(R.id.navigation_view);

        toolBar = (Toolbar) findViewById(R.id.toolbar);

        toolBar.setTitle(R.string.app_name);

        setSupportActionBar(toolBar);

        if (savedInstanceState != null) {
            mainFragment = (FragmentMain) getSupportFragmentManager().findFragmentByTag("mainfragment");
        } else if (findViewById(R.id.fragment_container) != null) {
            mainFragment = new FragmentMain();
            mainFragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, mainFragment, "mainfragment").commit();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (serviceBinder != null)
            serviceBinder.doUnbindService();
    }

      @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // No menu

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREFS_REQ) {
            eventBus.post(new EventPreferencesChanged());
        }
    }
}
