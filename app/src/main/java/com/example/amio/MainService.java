package com.example.amio;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MainService - Background service that periodically fetches sensor data from IoTLab API
 *
 * This service runs a TimerTask at fixed intervals to check sensor data,
 * detect light changes, and trigger notifications/emails based on configured rules.
 *
 * TP1: Basic service structure with TimerTask
 * TP2: HTTP fetching and JSON parsing
 * TP3: Communication with MainActivity via broadcasts
 */
public class MainService extends Service {

    private static final String TAG = "MainService";

    // Broadcast action for sending results to MainActivity
    public static final String ACTION_RESULT = "com.example.amio.ACTION_RESULT";
    public static final String ACTION_REQUEST_UPDATE = "com.example.amio.ACTION_REQUEST_UPDATE";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_SENSOR_COUNT = "sensor_count";
    public static final String EXTRA_LIGHTS_ON_COUNT = "lights_on_count";
    public static final String EXTRA_SENSOR_DETAILS = "sensor_details";

    // Timer for periodic task execution
    private Timer timer;
    private TimerTask task;

    // SharedPreferences for reading user settings
    private SharedPreferences prefs;

    // Fetch interval in milliseconds (default: 60 seconds)
    private static final long FETCH_INTERVAL_MS = 60_000;

    // Sensor state tracking - thread-safe map
    private final ConcurrentHashMap<String, SensorState> sensorStates = new ConcurrentHashMap<>();

    // Light detection threshold (calibration value)
    private double lightThreshold = SensorState.DEFAULT_LIGHT_THRESHOLD;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        // Initialize SharedPreferences using Context directly
        prefs = getSharedPreferences("amio_prefs", MODE_PRIVATE);

        // Read threshold from preferences (with default)
        lightThreshold = Double.parseDouble(
            prefs.getString("light_threshold", String.valueOf(SensorState.DEFAULT_LIGHT_THRESHOLD))
        );

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
     * TP2: Implement actual HTTP request and JSON parsing
     */
    private void fetchDataFromServer() {
        Log.d(TAG, "Fetching data from server...");

        // Get server URL from preferences (default: http://37.59.110.9:8080/AMIO-API)
        // The URL returns all sensor data directly as a JSON array
        String urlStr = prefs.getString("server_url", "http://37.59.110.9:8080/AMIO-API");

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000); // 10 seconds timeout
            conn.setReadTimeout(10000);

            Log.d(TAG, "Fetching from URL: " + urlStr);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response body
                InputStream inputStream = conn.getInputStream();
                String jsonResponse = convertStreamToString(inputStream);
                inputStream.close();

                Log.d(TAG, "JSON Response: " + jsonResponse);

                // Parse JSON and update sensor states
                parseJsonAndUpdateStates(jsonResponse);

                // Broadcast successful update to MainActivity
                broadcastResult("Data fetched successfully", jsonResponse);

            } else {
                Log.e(TAG, "HTTP request failed with code: " + responseCode);
                broadcastResult("HTTP Error: " + responseCode, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from server", e);
            broadcastResult("Fetch error: " + e.getMessage(), null);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Convert InputStream to String (helper method for HTTP response reading)
     *
     * @param is InputStream to read
     * @return String content
     */
    private String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Parse JSON response and update sensor states.
     * Detect light state changes and trigger actions (notifications/emails).
     *
     * Expected JSON format (object with "data" array from server):
     * {
     *   "data": [
     *     {
     *       "timestamp": 1234567890,
     *       "label": "light1",
     *       "value": 250.5,
     *       "mote": "m3-1"
     *     },
     *     ...
     *   ]
     * }
     *
     * @param jsonResponse JSON string from server
     */
    private void parseJsonAndUpdateStates(String jsonResponse) {
        try {
            // The server returns a JSON object with a "data" array property
            JSONObject rootObject = new JSONObject(jsonResponse);
            JSONArray dataArray = rootObject.getJSONArray("data");

            Log.d(TAG, "Parsing " + dataArray.length() + " sensor entries");

            int newLightsDetected = 0;

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                // Extract sensor data (using optX for robustness)
                long timestamp = item.optLong("timestamp", 0L);
                String label = item.optString("label", "unknown");
                double value = item.optDouble("value", Double.NaN);
                String mote = item.optString("mote", "unknown");

                // Skip invalid entries
                if (Double.isNaN(value) || mote.equals("unknown")) {
                    Log.w(TAG, "Skipping invalid sensor entry: " + item.toString());
                    continue;
                }

                // Check if this is a new light turned on
                SensorState existingState = sensorStates.get(mote);

                if (existingState == null) {
                    // First time seeing this sensor - create new state
                    SensorState newState = new SensorState(mote, label, value, timestamp);
                    sensorStates.put(mote, newState);

                    // If initially detected as ON, count as new light
                    if (newState.isLightOn()) {
                        Log.i(TAG, "New sensor detected with light ON: " + mote + " (value=" + value + ")");
                        newLightsDetected++;
                        handleNewLightDetected(mote, label, value, timestamp);
                    }
                } else {
                    // Check if light state changed from OFF to ON
                    if (existingState.detectNewLightOn(value, lightThreshold)) {
                        Log.i(TAG, "Light turned ON: " + mote + " (value=" + value + ")");
                        newLightsDetected++;
                        handleNewLightDetected(mote, label, value, timestamp);
                    }

                    // Update the state
                    existingState.updateState(value, timestamp, lightThreshold);
                }
            }

            Log.d(TAG, "Parsing complete. Total sensors tracked: " + sensorStates.size() +
                       ", New lights detected: " + newLightsDetected);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }

    /**
     * Handle detection of a new light turned on.
     * Determine appropriate action based on time and day (notification/email).
     *
     * TP2/TP3: Trigger notification or email based on configured rules
     *
     * @param mote Sensor identifier
     * @param label Sensor label
     * @param value Light sensor value
     * @param timestamp Timestamp of detection
     */
    private void handleNewLightDetected(String mote, String label, double value, long timestamp) {
        Log.d(TAG, "handleNewLightDetected: mote=" + mote + ", value=" + value);

        // TODO TP2/TP3: Implement time-based rules for notification/email
        // - Check current day (weekday vs weekend)
        // - Check current hour
        // - Trigger notification for weekday 19:00-23:00
        // - Trigger email for weekend 19:00-23:00 OR weekday 23:00-06:00

        // For now, just log the detection
        Log.i(TAG, String.format("Light detected: %s (%s) - value=%.2f at %d",
                                 mote, label, value, timestamp));
    }

    /**
     * Send broadcast to MainActivity with current status and data.
     * MainActivity will register a BroadcastReceiver to listen for these updates.
     *
     * @param status Current status message
     * @param jsonData Optional JSON data string
     */
    private void broadcastResult(String status, String jsonData) {
        Log.d(TAG, "broadcastResult() called - status: " + status);

        Intent intent = new Intent(ACTION_RESULT);
        // IMPORTANT: Set explicit package to ensure broadcast is delivered
        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());

        // Add sensor statistics
        intent.putExtra(EXTRA_SENSOR_COUNT, sensorStates.size());

        Log.d(TAG, "Total sensors in map: " + sensorStates.size());

        int lightsOnCount = 0;
        for (SensorState state : sensorStates.values()) {
            if (state.isLightOn()) {
                lightsOnCount++;
            }
        }
        intent.putExtra(EXTRA_LIGHTS_ON_COUNT, lightsOnCount);

        // Add JSON data if available
        if (jsonData != null) {
            intent.putExtra(EXTRA_DATA, jsonData);
        }

        // Add detailed sensor information as JSON array
        try {
            JSONArray sensorDetailsArray = new JSONArray();
            for (SensorState state : sensorStates.values()) {
                JSONObject sensorObject = new JSONObject();
                sensorObject.put("mote", state.getMote());
                sensorObject.put("label", state.getLabel());
                sensorObject.put("value", state.getValue());
                sensorObject.put("timestamp", state.getLastUpdated());
                sensorObject.put("lightOn", state.isLightOn());
                sensorDetailsArray.put(sensorObject);

                Log.d(TAG, "Added sensor to JSON: mote=" + state.getMote() +
                           ", value=" + state.getValue() +
                           ", lightOn=" + state.isLightOn());
            }

            String sensorDetailsJson = sensorDetailsArray.toString();
            intent.putExtra(EXTRA_SENSOR_DETAILS, sensorDetailsJson);

            Log.d(TAG, "Sensor details JSON length: " + sensorDetailsJson.length());
            if (sensorDetailsJson.length() < 500) {
                Log.d(TAG, "Sensor details JSON: " + sensorDetailsJson);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error building sensor details JSON", e);
            intent.putExtra(EXTRA_SENSOR_DETAILS, "[]");
        }

        // Send broadcast - use explicit Intent to ensure delivery
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent with action: " + ACTION_RESULT +
                   ", package: " + getPackageName() +
                   " (sensors=" + sensorStates.size() +
                   ", lights_on=" + lightsOnCount + ")");
    }

    /**
     * Get current sensor states (for debugging/testing)
     *
     * @return Map of sensor states
     */
    public Map<String, SensorState> getSensorStates() {
        return new ConcurrentHashMap<>(sensorStates);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        // Check if this is a request for immediate update
        if (intent != null && ACTION_REQUEST_UPDATE.equals(intent.getAction())) {
            Log.d(TAG, "Received request for immediate update - broadcasting current state");
            broadcastResult("Current state", null);
        }

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
