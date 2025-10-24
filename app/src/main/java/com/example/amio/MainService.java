package com.example.amio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
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

    // Notification channel/id
    private static final String CHANNEL_ID = "amio_alerts_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 1000; // 5 seconds for testing

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

    // Track last notification time per sensor to prevent spam
    private final ConcurrentHashMap<String, Long> lastNotificationTime = new ConcurrentHashMap<>();

    // Light detection threshold (calibration value)
    private double lightThreshold = SensorState.DEFAULT_LIGHT_THRESHOLD;

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

        // Create notification channel for alerts
        createNotificationChannel();

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
            java.util.List<String> motesJustTurnedOn = new java.util.ArrayList<>();
            java.util.List<String> motesJustTurnedOff = new java.util.ArrayList<>();

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
                sendGroupedNotification(motesJustTurnedOn, motesJustTurnedOff);
            }

            Log.d(TAG, "Parsing complete. Total sensors tracked: " + sensorStates.size() +
                       ", Turned ON: " + motesJustTurnedOn.size() +
                       ", Turned OFF: " + motesJustTurnedOff.size());

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }

    private void handleNewLightDetected(String mote, String label, double value, long timestamp) {
        // This method is now deprecated - replaced by sendGroupedNotification
        Log.d(TAG, "handleNewLightDetected: mote=" + mote + ", value=" + value);

        try {
            vibrateDevice(200);
        } catch (Exception e) {
            Log.e(TAG, "Error in handleNewLightDetected", e);
        }
    }

    /**
     * Send a grouped notification showing all lights that just changed state
     */
    private void sendGroupedNotification(java.util.List<String> motesOn, java.util.List<String> motesOff) {
        Log.d(TAG, "sendGroupedNotification() - ON: " + motesOn.size() + ", OFF: " + motesOff.size());

        // Cooldown check - use a global key for grouped notifications
        String cooldownKey = "grouped_notification";
        Long lastTime = lastNotificationTime.get(cooldownKey);
        long currentTime = System.currentTimeMillis();
        if (lastTime != null && (currentTime - lastTime) < NOTIFICATION_COOLDOWN_MS) {
            long remainingCooldown = (NOTIFICATION_COOLDOWN_MS - (currentTime - lastTime)) / 1000;
            Log.d(TAG, "Notification cooldown active - wait " + remainingCooldown + " seconds");
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build title
        String title;
        int totalChanges = motesOn.size() + motesOff.size();
        if (totalChanges == 1) {
            if (!motesOn.isEmpty()) {
                title = "💡 Lumière allumée: " + motesOn.get(0);
            } else {
                title = "🌙 Lumière éteinte: " + motesOff.get(0);
            }
        } else {
            title = "🔄 " + totalChanges + " changements détectés";
        }

        // Build detailed text
        StringBuilder text = new StringBuilder();

        if (!motesOn.isEmpty()) {
            text.append("💡 ALLUMÉES: ");
            for (int i = 0; i < motesOn.size(); i++) {
                text.append(motesOn.get(i));
                if (i < motesOn.size() - 1) {
                    text.append(", ");
                }
            }
        }

        if (!motesOff.isEmpty()) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append("🌙 ÉTEINTES: ");
            for (int i = 0; i < motesOff.size(); i++) {
                text.append(motesOff.get(i));
                if (i < motesOff.size() - 1) {
                    text.append(", ");
                }
            }
        }

        // Build big text style for expanded view
        StringBuilder bigText = new StringBuilder();
        if (!motesOn.isEmpty()) {
            bigText.append("💡 LUMIÈRES ALLUMÉES:\n");
            for (String mote : motesOn) {
                bigText.append("  • ").append(mote).append("\n");
            }
        }
        if (!motesOff.isEmpty()) {
            if (bigText.length() > 0) {
                bigText.append("\n");
            }
            bigText.append("🌙 LUMIÈRES ÉTEINTES:\n");
            for (String mote : motesOff) {
                bigText.append("  • ").append(mote).append("\n");
            }
        }

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bigText.toString()))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(alarmSound)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                lastNotificationTime.put(cooldownKey, currentTime);
                vibrateDevice(200);
                Log.i(TAG, "Grouped notification posted (ON:" + motesOn.size() + ", OFF:" + motesOff.size() + ")");
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            lastNotificationTime.put(cooldownKey, currentTime);
            vibrateDevice(200);
            Log.i(TAG, "Grouped notification posted (ON:" + motesOn.size() + ", OFF:" + motesOff.size() + ")");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AMIO Alerts";
            String description = "Notifications for detected lights left on";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + CHANNEL_ID);
            }
        }
    }

    private void sendNotification(String mote, String label, double value, long timestamp) {
        Log.d(TAG, "sendNotification() mote=" + mote);

        // Cooldown check
        Long lastTime = lastNotificationTime.get(mote);
        long currentTime = System.currentTimeMillis();
        if (lastTime != null && (currentTime - lastTime) < NOTIFICATION_COOLDOWN_MS) {
            long remainingCooldown = (NOTIFICATION_COOLDOWN_MS - (currentTime - lastTime)) / 1000;
            Log.d(TAG, "Notification cooldown active for mote=" + mote + " - wait " + remainingCooldown + " seconds");
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Lumière détectée: " + mote;
        String text = String.format(java.util.Locale.getDefault(),
            "Capteur %s (%s) détecte une lumière (val=%.2f)", mote, label, value);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(alarmSound)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                lastNotificationTime.put(mote, currentTime);
                Log.i(TAG, "Notification posted for mote=" + mote);
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            lastNotificationTime.put(mote, currentTime);
            Log.i(TAG, "Notification posted for mote=" + mote);
        }
    }

    private void sendEmailIntent(String mote, String label, double value, long timestamp) {
        Log.d(TAG, "sendEmailIntent() mote=" + mote);

        String emailAddress = prefs.getString("notify_email", "");
        String subject = "AMIO - Lumière détectée: " + mote;
        String body = String.format(java.util.Locale.getDefault(),
            "Capteur %s (%s) détecte une lumière à %d (val=%.2f)", mote, label, timestamp, value);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        if (emailAddress != null && !emailAddress.isEmpty()) {
            emailIntent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + Uri.encode(emailAddress)));
        }
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(Intent.createChooser(emailIntent, "Envoyer un email..."));
            Log.i(TAG, "Email chooser launched");
        } catch (Exception e) {
            Log.e(TAG, "No email client available", e);
        }
    }

    private void vibrateDevice(long millis) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(millis,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(millis);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Vibration failed", e);
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
