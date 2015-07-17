package net.kismetwireless.android.smarterwifimanager.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.squareup.otto.Bus;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.events.EventPreferencesChanged;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import javax.inject.Inject;


// Main icon color shifts
// 00e8d5    b8b8b8    a40000

public class MainActivity extends AppCompatActivity {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder serviceBinder;

    @Inject
    Bus eventBus;

    private static int PREFS_REQ = 1;

    private Toolbar toolBar;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private NavigationView navigationView;

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
        // getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /*
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                toolBar, R.string.drawer_open, R.string.drawer_close) {

            public void onDrawerClosed(View view) {
                getSupportActionBar().setTitle(getTitle());
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setTitle(getTitle());
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        drawerLayout.setDrawerListener(drawerToggle);
        */

        if (savedInstanceState != null) {
            mainFragment = (FragmentMain) getSupportFragmentManager().findFragmentByTag("mainfragment");
        } else if (findViewById(R.id.fragment_container) != null) {
            mainFragment = new FragmentMain();
            mainFragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, mainFragment, "mainfragment").commit();
        }

        // Configure the nav view
        /*
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.navigation_learned:
                        startActivity(new Intent(MainActivity.this, ActivitySsidLearned.class));
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.navigation_ignore:
                        startActivity(new Intent(MainActivity.this, ActivitySsidBlacklist.class));
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.navigation_bluetooth:
                        startActivity(new Intent(MainActivity.this, ActivityBluetoothBlacklist.class));
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.navigation_time:
                        startActivity(new Intent(MainActivity.this, ActivityTimeRange.class));
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.navigation_settings:
                        startActivityForResult(new Intent(MainActivity.this, ActivityPrefs.class), PREFS_REQ);
                        drawerLayout.closeDrawers();
                        return true;
                }
                return false;
            }
        });
        */
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
        // Sync the toggle state after onRestoreInstanceState has occurred.
        // drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // drawerToggle.onConfigurationChanged(newConfig);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // No menu

        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
          return true;
        }

        if (item.getItemId() == R.id.action_settings) {
            startActivityForResult(new Intent(MainActivity.this, ActivityPrefs.class), PREFS_REQ);
            return true;
        }

        return true;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PREFS_REQ) {
            eventBus.post(new EventPreferencesChanged());
        }
    }
}
