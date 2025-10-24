package com.example.amio;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
import java.util.List;
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

    // Fetch interval in milliseconds
    private static final long FETCH_INTERVAL_MS = 5_000;

    // Sensor state tracking - thread-safe map
    private final ConcurrentHashMap<String, SensorState> sensorStates = new ConcurrentHashMap<>();

    // Light detection threshold (calibration value)
    private double lightThreshold = SensorState.DEFAULT_LIGHT_THRESHOLD;

    // Notification helper for all notification-related operations
    private NotificationHelper notificationHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        // Initialize SharedPreferences using Context directly
        prefs = getSharedPreferences("amio_prefs", MODE_PRIVATE);

        // Read threshold from preferences (with default)
        try {
            lightThreshold = Double.parseDouble(
                prefs.getString("light_threshold", String.valueOf(SensorState.DEFAULT_LIGHT_THRESHOLD))
            );
        } catch (Exception e) {
            Log.w(TAG, "Invalid threshold in preferences, using default", e);
            lightThreshold = SensorState.DEFAULT_LIGHT_THRESHOLD;
        }

        // Initialize notification helper
        notificationHelper = new NotificationHelper(this);

        // Start periodic data fetching
        startPeriodicFetch();
    }

    private void startPeriodicFetch() {
        Log.d(TAG, "Starting periodic fetch task");

        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                fetchDataFromServer();
            }
        };

        timer.scheduleAtFixedRate(task, 0, FETCH_INTERVAL_MS);
    }

    private void fetchDataFromServer() {
        Log.d(TAG, "Fetching data from server...");

        String urlStr = prefs.getString("server_url", "http://37.59.110.9:8080/AMIO-API");

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            Log.d(TAG, "Fetching from URL: " + urlStr);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = conn.getInputStream();
                String jsonResponse = convertStreamToString(inputStream);
                inputStream.close();

                Log.d(TAG, "JSON Response: " + jsonResponse);

                parseJsonAndUpdateStates(jsonResponse);
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

    private void parseJsonAndUpdateStates(String jsonResponse) {
        try {
            JSONObject rootObject = new JSONObject(jsonResponse);
            JSONArray dataArray = rootObject.getJSONArray("data");

            Log.d(TAG, "Parsing " + dataArray.length() + " sensor entries");

            // Track changes for grouped notification
            List<String> motesJustTurnedOn = new ArrayList<>();
            List<String> motesJustTurnedOff = new ArrayList<>();

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                long timestamp = item.optLong("timestamp", 0L);
                String label = item.optString("label", "unknown");
                double value = item.optDouble("value", Double.NaN);
                String mote = item.optString("mote", "unknown");

                if (Double.isNaN(value) || mote.equals("unknown")) {
                    Log.w(TAG, "Skipping invalid sensor entry");
                    continue;
                }

                SensorState existingState = sensorStates.get(mote);

                if (existingState == null) {
                    SensorState newState = new SensorState(mote, label, value, timestamp);
                    sensorStates.put(mote, newState);

                    if (newState.isLightOn()) {
                        Log.i(TAG, "New sensor detected with light ON: " + mote + " (value=" + value + ")");
                        motesJustTurnedOn.add(mote);
                    }
                } else {
                    boolean wasOn = existingState.isLightOn();

                    // Update the state first
                    existingState.updateState(value, timestamp, lightThreshold);

                    boolean isNowOn = existingState.isLightOn();

                    // Detect changes
                    if (!wasOn && isNowOn) {
                        Log.i(TAG, "Light turned ON: " + mote + " (value=" + value + ")");
                        motesJustTurnedOn.add(mote);
                    } else if (wasOn && !isNowOn) {
                        Log.i(TAG, "Light turned OFF: " + mote + " (value=" + value + ")");
                        motesJustTurnedOff.add(mote);
                    }
                }
            }

            // Send grouped notification if there are changes
            if (!motesJustTurnedOn.isEmpty() || !motesJustTurnedOff.isEmpty()) {
                notificationHelper.sendGroupedNotification(motesJustTurnedOn, motesJustTurnedOff);
            }

            Log.d(TAG, "Parsing complete. Total sensors tracked: " + sensorStates.size() +
                       ", Turned ON: " + motesJustTurnedOn.size() +
                       ", Turned OFF: " + motesJustTurnedOff.size());

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }


    private void broadcastResult(String status, String jsonData) {
        Log.d(TAG, "broadcastResult() called - status: " + status);

        Intent intent = new Intent(ACTION_RESULT);
        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        intent.putExtra(EXTRA_SENSOR_COUNT, sensorStates.size());

        int lightsOnCount = 0;
        for (SensorState state : sensorStates.values()) {
            if (state.isLightOn()) {
                lightsOnCount++;
            }
        }
        intent.putExtra(EXTRA_LIGHTS_ON_COUNT, lightsOnCount);

        if (jsonData != null) {
            intent.putExtra(EXTRA_DATA, jsonData);
        }

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
            }

            String sensorDetailsJson = sensorDetailsArray.toString();
            intent.putExtra(EXTRA_SENSOR_DETAILS, sensorDetailsJson);

            Log.d(TAG, "Sensor details JSON length: " + sensorDetailsJson.length());
        } catch (JSONException e) {
            Log.e(TAG, "Error building sensor details JSON", e);
            intent.putExtra(EXTRA_SENSOR_DETAILS, "[]");
        }

        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent - sensors=" + sensorStates.size() + ", lights_on=" + lightsOnCount);
    }

    public Map<String, SensorState> getSensorStates() {
        return new ConcurrentHashMap<>(sensorStates);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        if (intent != null && ACTION_REQUEST_UPDATE.equals(intent.getAction())) {
            Log.d(TAG, "Received request for immediate update");
            broadcastResult("Current state", null);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

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
        return null;
    }
}
