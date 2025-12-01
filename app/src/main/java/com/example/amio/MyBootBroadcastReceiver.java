package com.example.amio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * MyBootBroadcastReceiver - Receives BOOT_COMPLETED broadcast
 * 
 * Automatically starts MainService when device boots, if enabled in preferences.
 * 
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml
 */
public class MyBootBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "MyBootBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "BOOT_COMPLETED received");

            SharedPreferences prefs = context.getSharedPreferences("amio_prefs", Context.MODE_PRIVATE);
            boolean startServiceOnBoot = prefs.getBoolean("start_service_on_boot", false);

            Log.d(TAG, "Start on boot preference: " + startServiceOnBoot);

            if (startServiceOnBoot) {
                try {
                    // Update service enabled preference for UI sync
                    SharedPreferences settingsPrefs = context.getSharedPreferences("amio_settings", Context.MODE_PRIVATE);
                    settingsPrefs.edit().putBoolean("pref_service_enabled", true).apply();
                    Log.d(TAG, "Updated pref_service_enabled to true");

                    // Start the service
                    Intent serviceIntent = new Intent(context, MainService.class);
                    context.startService(serviceIntent);

                    Log.i(TAG, "MainService start requested successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Error starting MainService at boot", e);
                }
            } else {
                Log.d(TAG, "Start on boot disabled, service not started");
            }
        } else {
            Log.d(TAG, "Received intent action: " + intent.getAction());
        }
    }
}
