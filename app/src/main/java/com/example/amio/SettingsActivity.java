package com.example.amio;

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
}
