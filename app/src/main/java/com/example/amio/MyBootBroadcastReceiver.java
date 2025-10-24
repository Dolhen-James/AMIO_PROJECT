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
 * TP3: Boot receiver implementation
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml
 */
public class MyBootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("amio_prefs", Context.MODE_PRIVATE);
            boolean startServiceOnBoot = prefs.getBoolean("start_service_on_boot", false);
            Log.d("MyBootBroadcastReceiver", "onReceive: startServiceOnBoot=" + startServiceOnBoot);

            if (startServiceOnBoot) {
                Intent serviceIntent = new Intent(context, MainService.class);
                context.startService(serviceIntent);
                Log.d("MyBootBroadcastReceiver", "onReceive: MainService started");
            }
        }
    }
}
