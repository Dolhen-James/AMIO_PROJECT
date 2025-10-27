package com.example.amio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceBroadcastReceiver";
    private final ServiceBroadcastCallback callback;

    public ServiceBroadcastReceiver(ServiceBroadcastCallback callback) {
        this.callback = callback;
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
            String sensorDataJson = intent.getStringExtra(MainService.EXTRA_SENSOR_DETAILS);

            Log.d(TAG, "Received broadcast - status: " + status +
                    ", sensors: " + sensorCount +
                    ", lights_on: " + lightsOnCount);
            Log.d(TAG, "Sensor JSON length: " + (sensorDataJson != null ? sensorDataJson.length() : "null"));
            if (sensorDataJson != null && sensorDataJson.length() < 500) {
                Log.d(TAG, "Sensor JSON: " + sensorDataJson);
            }

            // Notify callback
            if (callback != null) {
                callback.onServiceBroadcast(status, timestamp, sensorCount, lightsOnCount, sensorDataJson);
            }

            Log.d(TAG, "Callback notified");
        } else {
            Log.w(TAG, "Received broadcast with unexpected action: " + intent.getAction());
        }
    }
}
