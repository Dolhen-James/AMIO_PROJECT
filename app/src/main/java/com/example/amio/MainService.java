package com.example.amio;

import android.app.Service;
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
 */
public class MainService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

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
    public static final String EXTRA_FETCH_ERRORS = "fetch_errors";

    // Timer for periodic task execution
    private Timer timer;
    private TimerTask task;

    // SharedPreferences for reading user settings
    private SharedPreferences prefs;

    // Fetch interval in milliseconds (dynamic)
    private long fetchIntervalMs = 5000;

    // Light-Mote state tracking - thread-safe map
    // Key format: "light1_9.138" (lightLabel_moteId)
    private final ConcurrentHashMap<String, LightMoteState> lightMoteStates = new ConcurrentHashMap<>();

    // List of lights to monitor
    private static final String[] LIGHT_LABELS = {"light1", "light2"};


    // Notification helper for all notification-related operations
    private NotificationHelper notificationHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");

        // Initialize SharedPreferences using Context directly
        prefs = getSharedPreferences("amio_settings", MODE_PRIVATE);

        // Listen for polling interval or other setings changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Get initial polling interval
        fetchIntervalMs = getPollingIntervalMs();
        //Log.d(TAG, "onCreate() - Polling interval: " + fetchIntervalMs + " ms");


        // Initialize notification helper (for push notifications)
        notificationHelper = new NotificationHelper(this);
        //Log.d(TAG, "onCreate() - NotificationHelper initialized");
        // Start periodic data fetching
        startPeriodicFetch();

        // Broadcast initial state for UI sync
        broadcastResultWithErrors("Service started", null, new ArrayList<>());

        //Log.i(TAG, "onCreate() - Service initialization complete");

    }

    private void startPeriodicFetch() {
        //Log.d(TAG, "Starting periodic fetch task with interval: " + fetchIntervalMs + " ms");
        stopPeriodicFetch();
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                fetchDataFromServer();
            }
        };
        timer.scheduleAtFixedRate(task, 0, fetchIntervalMs);
    }

    private void stopPeriodicFetch() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private long getPollingIntervalMs() {
        String intervalStr = prefs.getString("pref_polling_interval", "10");
        try {
            long seconds = Long.parseLong(intervalStr);
            return Math.max(1, seconds) * 1000;
        } catch (Exception e) {
            return 10000;
        }
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("pref_polling_interval".equals(key)) {
            fetchIntervalMs = getPollingIntervalMs();
            startPeriodicFetch();
            //Log.d(TAG, "Polling interval changed, timer restarted: " + fetchIntervalMs + " ms");
        }
    }

    private void fetchDataFromServer() {
        //Log.d(TAG, "Fetching data from server for all lights...");

        String baseUrl = prefs.getString("server_url", "http://peniche.pakbo-et-lombrik.fr:8000"); // ceci est notre mock api

        // Track changes across all lights for grouped notification
        List<String> allMotesJustTurnedOn = new ArrayList<>();
        List<String> allMotesJustTurnedOff = new ArrayList<>();

        // Track fetch errors
        List<String> fetchErrors = new ArrayList<>();
        int successfulFetches = 0;

        // Fetch data for each light
        for (String lightLabel : LIGHT_LABELS) {
            String urlStr = baseUrl + "/iotlab/rest/data/1/" + lightLabel + "/last"; // le chemin est le même pour notre mock api ou le serveur réel

            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                //Log.d(TAG, "Fetching " + lightLabel + " from URL: " + urlStr);
                int responseCode = conn.getResponseCode();
                //Log.d(TAG, lightLabel + " - HTTP Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = conn.getInputStream();
                    String jsonResponse = convertStreamToString(inputStream);
                    inputStream.close();

                    //Log.d(TAG, lightLabel + " - JSON Response: " + jsonResponse);
                    parseJsonForLight(lightLabel, jsonResponse, allMotesJustTurnedOn, allMotesJustTurnedOff);
                    successfulFetches++;

                } else {
                    Log.e(TAG, lightLabel + " - HTTP request failed with code: " + responseCode);
                    fetchErrors.add(lightLabel + " (HTTP " + responseCode + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error fetching data for " + lightLabel, e);
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = e.getClass().getSimpleName();
                }
                fetchErrors.add(lightLabel + " (" + errorMsg + ")");
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        // Send grouped notification if there are changes
        if (!allMotesJustTurnedOn.isEmpty() || !allMotesJustTurnedOff.isEmpty()) {
            notificationHelper.sendGroupedNotification(allMotesJustTurnedOn, allMotesJustTurnedOff);
        }

        // Broadcast results to UI with error information
        String status;
        if (fetchErrors.isEmpty()) {
            status = "Data fetched successfully";
            //Log.d(TAG, "All fetches successful - no errors");
        } else if (successfulFetches == 0) {
            status = "Failed to fetch data";
            Log.e(TAG, "All fetches failed - " + fetchErrors.size() + " errors");
        } else {
            status = "Partial fetch (" + successfulFetches + "/" + LIGHT_LABELS.length + " successful)";
            Log.w(TAG, "Partial success - " + successfulFetches + " succeeded, " + fetchErrors.size() + " failed");
        }

        //Log.d(TAG, "Fetch complete - Status: " + status + ", Errors: " + fetchErrors);
        broadcastResultWithErrors(status, null, fetchErrors);
    }

    /**
     * Will be used to convert the answer from the API server to json format
     * @param is answer from HTTP server
     * @return answer as String
     * @throws Exception
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
     * Parse JSON data for a specific light and update states
     * @param lightLabel The light being monitored (e.g., "light1")
     * @param jsonResponse The JSON response for this light
     * @param motesJustTurnedOn List to accumulate motes that turned on
     * @param motesJustTurnedOff List to accumulate motes that turned off
     */
    private void parseJsonForLight(String lightLabel, String jsonResponse,
                                   List<String> motesJustTurnedOn,
                                   List<String> motesJustTurnedOff) {
        try {
            JSONObject rootObject = new JSONObject(jsonResponse);
            JSONArray dataArray = rootObject.getJSONArray("data");

            //Log.d(TAG, "Parsing " + dataArray.length() + " mote entries for " + lightLabel);

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                long timestamp = item.optLong("timestamp", 0L);
                String label = item.optString("label", "unknown");
                double value = item.optDouble("value", Double.NaN);
                String moteId = item.optString("mote", "unknown");

                if (Double.isNaN(value) || moteId.equals("unknown")) {
                    Log.w(TAG, "Skipping invalid mote entry");
                    continue;
                }

                // Create unique key for this light-mote combination
                String uniqueKey = LightMoteState.createKey(lightLabel, moteId);

                LightMoteState existingState = lightMoteStates.get(uniqueKey);

                if (existingState == null) {
                    // New light-mote combination detected
                    LightMoteState newState = new LightMoteState(lightLabel, moteId, value, timestamp);
                    lightMoteStates.put(uniqueKey, newState);

                    if (newState.isLightOn()) {
                        Log.i(TAG, "Light ON detected: " + lightLabel + "/" + moteId + " (" + value + " lux)");
                        motesJustTurnedOn.add(lightLabel + " - Mote " + moteId);
                    }
                } else {
                    // Update existing state
                    boolean wasOn = existingState.isLightOn();
                    double prevValue = existingState.getCurrentValue();
                    boolean statusChanged = existingState.updateState(value, timestamp);
                    boolean isNowOn = existingState.isLightOn();

                    //Log.d(TAG, "Update mote: " + lightLabel + " - " + moteId +
                    //        " | prev=" + prevValue + " | new=" + value +
                    //        " | wasOn=" + wasOn + " | isNowOn=" + isNowOn +
                    //        " | changed=" + statusChanged);

                    // Detect changes
                    if (statusChanged) {
                        if (!wasOn && isNowOn) {
                            Log.i(TAG, "Light ON: " + lightLabel + "/" + moteId + " (" + value + " lux)");
                            motesJustTurnedOn.add(lightLabel + " - Mote " + moteId);
                        } else if (wasOn && !isNowOn) {
                            Log.i(TAG, "Light OFF: " + lightLabel + "/" + moteId + " (" + value + " lux)");
                            motesJustTurnedOff.add(lightLabel + " - Mote " + moteId);
                        }
                    }
                }
            }

            //Log.d(TAG, "Parsing complete for " + lightLabel + ". Total light-mote combinations tracked: " + lightMoteStates.size());

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON for " + lightLabel, e);
        }
    }


    /**
     * Broadcast service results with fetch error information to MainActivity, enables better debugging and UI feedback
     */
    private void broadcastResultWithErrors(String status, String jsonData, List<String> fetchErrors) {
        //Log.d(TAG, "broadcastResultWithErrors() called - status: " + status + ", errors: " + (fetchErrors != null ? fetchErrors.size() : "null"));

        //if (fetchErrors != null && !fetchErrors.isEmpty()) {
        //    Log.d(TAG, "Errors to broadcast: " + fetchErrors);
        //}

        Intent intent = new Intent(ACTION_RESULT);
        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        intent.putExtra(EXTRA_SENSOR_COUNT, lightMoteStates.size());

        int lightsOnCount = 0;
        for (LightMoteState state : lightMoteStates.values()) {
            if (state.isLightOn()) {
                lightsOnCount++;
            }
        }
        intent.putExtra(EXTRA_LIGHTS_ON_COUNT, lightsOnCount);

        if (jsonData != null) {
            intent.putExtra(EXTRA_DATA, jsonData);
        }

        // Add fetch errors as JSON array
        if (fetchErrors != null && !fetchErrors.isEmpty()) {
            try {
                JSONArray errorsArray = new JSONArray();
                for (String error : fetchErrors) {
                    errorsArray.put(error);
                }
                String errorsJson = errorsArray.toString();
                intent.putExtra(EXTRA_FETCH_ERRORS, errorsJson);
                //Log.d(TAG, "Added fetch errors to intent: " + errorsJson);
            } catch (Exception e) {
                Log.e(TAG, "Error building fetch errors JSON", e);
            }
        }
        //else {
        //    Log.d(TAG, "No fetch errors to broadcast");
        //}

        try {
            JSONArray sensorDetailsArray = new JSONArray();
            for (LightMoteState state : lightMoteStates.values()) {
                JSONObject sensorObject = new JSONObject();
                sensorObject.put("light", state.getLightLabel());
                sensorObject.put("mote", state.getMoteId());
                sensorObject.put("value", state.getCurrentValue());
                sensorObject.put("timestamp", state.getLastUpdated());
                sensorObject.put("lightOn", state.isLightOn());
                sensorDetailsArray.put(sensorObject);
            }

            String sensorDetailsJson = sensorDetailsArray.toString();
            intent.putExtra(EXTRA_SENSOR_DETAILS, sensorDetailsJson);

            //Log.d(TAG, "Sensor details JSON length: " + sensorDetailsJson.length());
        } catch (JSONException e) {
            Log.e(TAG, "Error building sensor details JSON", e);
            intent.putExtra(EXTRA_SENSOR_DETAILS, "[]");
        }

        sendBroadcast(intent);
        //Log.d(TAG, "Broadcast sent with errors - light-mote combinations=" + lightMoteStates.size() + ", lights_on=" + lightsOnCount + ", errors=" + fetchErrors.size());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.i(TAG, "onStartCommand() - Service command received. flags=" + flags + ", startId=" + startId);

        if (intent != null && ACTION_REQUEST_UPDATE.equals(intent.getAction())) {
            //Log.d(TAG, "Received request for immediate update");
            broadcastResultWithErrors("Current state", null, new ArrayList<>());
        }

        //Log.i(TAG, "onStartCommand() - Returning START_STICKY to ensure service restarts after kill");
        return START_STICKY; // comme vu en TP
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");

            stopPeriodicFetch();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
