
package com.example.amio;

import android.content.Intent;
import com.example.amio.MainService;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.View;
import android.view.ViewGroup;

/**
 * SettingsActivity - Settings screen for the AMIO application
 *
 * This activity uses the deprecated PreferenceActivity approach
 * to provide user-configurable settings such as notification preferences,
 * polling intervals, sensor thresholds, etc.
 */
public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Get reference to the "Enable Service" CheckBoxPreference
        android.preference.CheckBoxPreference servicePref = (android.preference.CheckBoxPreference) findPreference("pref_service_enabled");
        SettingsManager settingsManager = new SettingsManager(this);

        // Sync checkbox state with SharedPreferences
        boolean enabled = settingsManager.getBoolean("pref_service_enabled", true);
        servicePref.setChecked(enabled);

        // Listen for changes
        servicePref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isChecked = (Boolean) newValue;
            settingsManager.setBoolean("pref_service_enabled", isChecked);

            // Start or stop the service accordingly
            Intent serviceIntent = new Intent(this, MainService.class);
            if (isChecked) {
                startService(serviceIntent);
            } else {
                stopService(serviceIntent);
            }
            return true;
        });

            // Get reference to the "Enable Notifications" CheckBoxPreference
            android.preference.CheckBoxPreference notifPref = (android.preference.CheckBoxPreference) findPreference("pref_notifications_enabled");
            boolean notifEnabled = settingsManager.getBoolean("pref_notifications_enabled", true);
            notifPref.setChecked(notifEnabled);

            notifPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                settingsManager.setBoolean("pref_notifications_enabled", isChecked);
                // No need to do more, NotificationHelper will read this value
                return true;
            });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Fix for system bars overlap - apply fitsSystemWindows to the content view
        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setFitsSystemWindows(true);
            // Request layout to apply the changes
            contentView.requestLayout();
        }

        // Also apply to the list view if it exists
        View listView = findViewById(android.R.id.list);
        if (listView != null && listView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) listView.getLayoutParams();
            // Add top padding to avoid overlap with status bar (in pixels)
            int statusBarHeight = getStatusBarHeight();
            params.topMargin = statusBarHeight;
            listView.setLayoutParams(params);
        }
    }

    /**
     * Get the status bar height in pixels
     */
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

        @Override
        protected void onResume() {
            super.onResume();
            // Sync checkbox state if changed from MainActivity
            android.preference.CheckBoxPreference servicePref = (android.preference.CheckBoxPreference) findPreference("pref_service_enabled");
            SettingsManager settingsManager = new SettingsManager(this);
            boolean enabled = settingsManager.getBoolean("pref_service_enabled", true);
            servicePref.setChecked(enabled);

                // Sync notification checkbox state
                android.preference.CheckBoxPreference notifPref = (android.preference.CheckBoxPreference) findPreference("pref_notifications_enabled");
                boolean notifEnabled = settingsManager.getBoolean("pref_notifications_enabled", true);
                notifPref.setChecked(notifEnabled);
        }
}
