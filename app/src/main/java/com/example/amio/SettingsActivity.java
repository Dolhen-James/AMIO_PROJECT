
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
            // Polling Interval EditTextPreference: show current value as summary
            android.preference.EditTextPreference pollingPref = (android.preference.EditTextPreference) findPreference("pref_polling_interval");
            String pollingValue = settingsManager.getString("pref_polling_interval", "10");
            pollingPref.setSummary("Current: " + pollingValue + " seconds");

            pollingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newPollingValue = (String) newValue;
                settingsManager.setString("pref_polling_interval", newPollingValue);
                pollingPref.setSummary("Current: " + newPollingValue + " seconds");
                return true;
            });

            // Get reference to the "Enable Notifications" CheckBoxPreference
            android.preference.CheckBoxPreference notifPref = (android.preference.CheckBoxPreference) findPreference("pref_notifications_enabled_notif");
            boolean notifEnabled = settingsManager.getBoolean("pref_notifications_enabled_notif", true);
            notifPref.setChecked(notifEnabled);

            notifPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                settingsManager.setBoolean("pref_notifications_enabled_notif", isChecked);
                // No need to do more, NotificationHelper will read this value
                return true;
            });

            // Get reference to the "Vibrate" CheckBoxPreference
            android.preference.CheckBoxPreference vibratePref = (android.preference.CheckBoxPreference) findPreference("pref_vibrate_notif");
            boolean vibrateEnabled = settingsManager.getBoolean("pref_vibrate_notif", true);
            vibratePref.setChecked(vibrateEnabled);

            vibratePref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isChecked = (Boolean) newValue;
                settingsManager.setBoolean("pref_vibrate_notif", isChecked);
                return true;
            });
            // Log changes to notification days and clean up stored values
            android.preference.MultiSelectListPreference daysPref = (android.preference.MultiSelectListPreference) findPreference("pref_notification_days_notif");
            if (daysPref != null) {
                daysPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    java.util.Set<String> selectedDays = (java.util.Set<String>) newValue;
                    CharSequence[] entryValuesCs = daysPref.getEntryValues();
                    CharSequence[] entries = daysPref.getEntries();
                    String[] validDayValues = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
                    java.util.Set<String> cleanedDays = new java.util.HashSet<>();
                    StringBuilder displayNames = new StringBuilder();
                    for (String value : selectedDays) {
                        for (int i = 0; i < entryValuesCs.length; i++) {
                            if (entryValuesCs[i].toString().equals(value)) {
                                displayNames.append(entries[i]).append(" (").append(value).append(") ");
                            }
                        }
                        // Only add valid entry values
                        for (String valid : validDayValues) {
                            if (value.equals(valid)) {
                                cleanedDays.add(valid);
                            }
                        }
                    }
                    // Save cleaned set to SharedPreferences
                    android.content.SharedPreferences prefs = getSharedPreferences("amio_settings", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putStringSet("pref_notification_days_notif", cleanedDays);
                    editor.apply();
                    android.util.Log.d("SettingsActivity", "Notification days changed: values=" + cleanedDays + ", displayNames=" + displayNames.toString());
                    // Log actual stored values for verification
                    java.util.Set<String> storedDays = prefs.getStringSet("pref_notification_days_notif", new java.util.HashSet<>());
                    android.util.Log.d("SettingsActivity", "Stored notification days in SharedPreferences: " + storedDays);
                    String storedTimeRange = prefs.getString("pref_notification_time_range_notif", "08:00-20:00");
                    android.util.Log.d("SettingsActivity", "Stored notification time range in SharedPreferences: " + storedTimeRange);
                    return true;
                });
            }

            // Log changes to notification time range with detailed parsing
            com.example.amio.TimeRangePreference timeRangePref = (com.example.amio.TimeRangePreference) findPreference("pref_notification_time_range_notif");
            if (timeRangePref != null) {
                timeRangePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String newTimeRange = (String) newValue;
                    String[] parts = newTimeRange.split("-");
                    if (parts.length == 2) {
                        android.util.Log.d("SettingsActivity", "Notification time range changed: start=" + parts[0] + ", end=" + parts[1]);
                    } else {
                        android.util.Log.d("SettingsActivity", "Notification time range changed: invalid format: " + newTimeRange);
                    }
                    // Log actual stored value for verification
                    android.content.SharedPreferences prefs = getSharedPreferences("amio_settings", MODE_PRIVATE);
                    String storedTimeRange = prefs.getString("pref_notification_time_range_notif", "08:00-20:00");
                    android.util.Log.d("SettingsActivity", "Stored notification time range in SharedPreferences: " + storedTimeRange);
                    return true;
                });
            }
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
            if (servicePref != null) servicePref.setChecked(enabled);

            // Sync notification checkbox state
            android.preference.CheckBoxPreference notifPref = (android.preference.CheckBoxPreference) findPreference("pref_notifications_enabled_notif");
            boolean notifEnabled = settingsManager.getBoolean("pref_notifications_enabled_notif", true);
            if (notifPref != null) notifPref.setChecked(notifEnabled);

            // Sync vibrate checkbox state
            android.preference.CheckBoxPreference vibratePref = (android.preference.CheckBoxPreference) findPreference("pref_vibrate_notif");
            boolean vibrateEnabled = settingsManager.getBoolean("pref_vibrate_notif", true);
            if (vibratePref != null) vibratePref.setChecked(vibrateEnabled);

            // Sync polling interval summary
            android.preference.EditTextPreference pollingPref = (android.preference.EditTextPreference) findPreference("pref_polling_interval");
            String pollingValue = settingsManager.getString("pref_polling_interval", "10");
            if (pollingPref != null) pollingPref.setSummary("Current: " + pollingValue + " seconds");
        }
}
