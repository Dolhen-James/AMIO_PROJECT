package com.example.amio;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SettingsManager - A utility class for managing application settings using SharedPreferences.
 *
 * Provides methods to save and retrieve various types of settings.
 */
public class SettingsManager {
    private static final String PREF_NAME = "amio_settings";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public SettingsManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // Example: Save a boolean setting
    public void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    // Example: Save a string setting
    public void setString(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    // Add more getters/setters as needed for other types
}
