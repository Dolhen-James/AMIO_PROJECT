package com.example.amio;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.util.Timer;
import java.util.TimerTask;

/**
 * MainService - Background service that periodically fetches sensor data from IoTLab API
 *
 * This service runs a TimerTask at fixed intervals to check sensor data,
 * detect light changes, and trigger notifications/emails based on configured rules.
 *
 * TP1: Basic service structure with TimerTask
 * TP2: HTTP fetching and JSON parsing (to be implemented)
 * TP3: Communication with MainActivity via broadcasts
 */
public class MainService extends Service {

    private static final String TAG = "MainService";

    // Broadcast action for sending results to MainActivity
    public static final String ACTION_RESULT = "com.example.amio.ACTION_RESULT";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_DATA = "data";

    // Timer for periodic task execution
    private Timer timer;
    private TimerTask task;

    // SharedPreferences for reading user settings
    private SharedPreferences prefs;

    // Fetch interval in milliseconds (default: 60 seconds)
    private static final long FETCH_INTERVAL_MS = 60_000;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        // Initialize SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Start periodic data fetching
        startPeriodicFetch();
    }

    /**
     * Initialize and start the periodic fetch task using Timer and TimerTask.
     * The task runs immediately and then repeats at fixed intervals.
     */
    private void startPeriodicFetch() {
        Log.d(TAG, "Starting periodic fetch task");

        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                // This runs in a separate thread (not UI thread)
                fetchDataFromServer();
            }
        };

        // Schedule task: run immediately (delay=0), then repeat every FETCH_INTERVAL_MS
        timer.scheduleAtFixedRate(task, 0, FETCH_INTERVAL_MS);
    }

    /**
     * Fetch data from the IoTLab server and process it.
     *
     * TP1: Basic placeholder implementation
     * TP2: Will implement actual HTTP request and JSON parsing
     */
    private void fetchDataFromServer() {
        Log.d(TAG, "Fetching data from server...");

        // Get server URL from preferences (default value for now)
        String serverUrl = prefs.getString("server_url", "http://37.59.110.9:8080/AMIO-API");

        // TP1: For now, just log and send a status update
        // TP2: Will implement actual HTTP request here
        String status = "Service running - fetch attempt at " + System.currentTimeMillis();

        // Broadcast result to MainActivity
        broadcastResult(status);

        // TODO TP2: Implement actual HTTP fetch with HttpURLConnection
        // TODO TP2: Parse JSON response with JsonReader or JSONObject
        // TODO TP2: Update sensor states and detect changes
        // TODO TP2: Trigger notifications/emails based on detection rules
    }

    /**
     * Send broadcast to MainActivity with current status and data.
     * MainActivity will register a BroadcastReceiver to listen for these updates.
     *
     * @param status Current status message
     */
    private void broadcastResult(String status) {
        Intent intent = new Intent(ACTION_RESULT);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        // TODO TP2: Add sensor data as extra

        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent: " + status);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        // Return START_STICKY so the service is restarted if killed by system
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // Cancel timer to stop the periodic task and release resources
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }
}
