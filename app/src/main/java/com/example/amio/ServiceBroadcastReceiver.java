package com.example.amio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceBroadcastReceiver";
    private final MainActivity mainActivity;

    public ServiceBroadcastReceiver(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "BroadcastReceiver.onReceive() called");

        if (MainService.ACTION_RESULT.equals(intent.getAction())) {
            Log.d(TAG, "Correct action received: " + MainService.ACTION_RESULT);

            // Extract data from broadcast
            String status = intent.getStringExtra(MainService.EXTRA_STATUS);
            long timestamp = intent.getLongExtra(MainService.EXTRA_TIMESTAMP, 0);
            int sensorCount = intent.getIntExtra(MainService.EXTRA_SENSOR_COUNT, 0);
            int lightsOnCount = intent.getIntExtra(MainService.EXTRA_LIGHTS_ON_COUNT, 0);

            // Extract detailed sensor data (JSON format)
            String sensorDataJson = intent.getStringExtra(MainService.EXTRA_SENSOR_DETAILS);

            Log.d(TAG, "Received broadcast - status: " + status +
                    ", sensors: " + sensorCount +
                    ", lights_on: " + lightsOnCount);
            Log.d(TAG, "Sensor JSON length: " + (sensorDataJson != null ? sensorDataJson.length() : "null"));
            if (sensorDataJson != null && sensorDataJson.length() < 500) {
                Log.d(TAG, "Sensor JSON: " + sensorDataJson);
            }

            // Update UI on main thread
            mainActivity.updateLastCheck(timestamp);
            mainActivity.updateSensorData(sensorCount, lightsOnCount, status, sensorDataJson);

            Log.d(TAG, "UI update completed");
        } else {
            Log.w(TAG, "Received broadcast with unexpected action: " + intent.getAction());
        }
    }
}
