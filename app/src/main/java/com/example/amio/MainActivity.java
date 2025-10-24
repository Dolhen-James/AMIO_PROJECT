package com.example.amio;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MainActivity - Main UI for the AMIO Light Detection application
 *
 * Features:
 * - Toggle button to start/stop MainService
 * - Display service status (running/stopped)
 * - Show last check timestamp
 * - Display sensor data (TP2)
 * - Request notification permission on Android 13+ (TP2/TP3)
 *
 * TP1: Basic service control and status display
 * TP2: Display sensor count and lights on count
 * TP3: BroadcastReceiver for service communication
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_POST_NOTIFICATIONS = 1001;

    // UI elements
    private Button btnToggleService;
    private TextView tvServiceStatus;
    private TextView tvLastCheck;
    private TextView tvSensorData;

    // Service state
    private boolean isServiceRunning = false;

    // BroadcastReceiver to receive updates from MainService
    private BroadcastReceiver serviceReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity created");

        // Request notification permission for Android 13+ (API 33+)
        requestNotificationPermission();

        // Initialize UI elements
        initializeViews();

        // Set up the toggle button
        setupToggleButton();

        // Check if service is already running
        updateServiceStatus();

        // Set up BroadcastReceiver for service updates
        setupBroadcastReceiver();
    }

    /**
     * Request POST_NOTIFICATIONS permission for Android 13+ (API 33+)
     * This is required for the app to display notifications
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted");
            }
        } else {
            Log.d(TAG, "Android version < 13, POST_NOTIFICATIONS not required");
        }
    }

    /**
     * Handle the result of permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted");
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied");
                Toast.makeText(this, "Notification permission denied - notifications will not work", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Initialize all UI elements by finding them by ID
     */
    private void initializeViews() {
        btnToggleService = findViewById(R.id.btnToggleService);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvLastCheck = findViewById(R.id.tvLastCheck);
        tvSensorData = findViewById(R.id.tvSensorData);

        // Initialize sensor data display
        tvSensorData.setText("No data yet - waiting for service...");
        Log.d(TAG, "UI elements initialized - tvSensorData is " + (tvSensorData != null ? "NOT NULL" : "NULL"));

    }

    /**
     * Set up the toggle button click listener to start/stop the service
     */
    private void setupToggleButton() {
        btnToggleService.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopService();
            } else {
                startService();
            }
        });
    }

    /**
     * Set up BroadcastReceiver to listen for updates from MainService (TP3)
     */
    private void setupBroadcastReceiver() {
        serviceReceiver = new BroadcastReceiver() {
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
                    updateLastCheck(timestamp);
                    updateSensorData(sensorCount, lightsOnCount, status, sensorDataJson);

                    Log.d(TAG, "UI update completed");
                } else {
                    Log.w(TAG, "Received broadcast with unexpected action: " + intent.getAction());
                }
            }
        };

        // Register receiver with IntentFilter
        IntentFilter filter = new IntentFilter(MainService.ACTION_RESULT);
        // From Android 13 (API 33) the registerReceiver call must explicitly declare whether
        // the receiver is exported. Use RECEIVER_NOT_EXPORTED because this receiver
        // is intended for app-internal communication with the service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceReceiver, filter);
        }

        Log.d(TAG, "BroadcastReceiver registered for action: " + MainService.ACTION_RESULT);
    }

    /**
     * Start the MainService
     */
    private void startService() {
        Log.d(TAG, "Starting MainService");
        Intent intent = new Intent(this, MainService.class);
        startService(intent);
        updateServiceStatus();
    }

    /**
     * Stop the MainService
     */
    private void stopService() {
        Log.d(TAG, "Stopping MainService");
        Intent intent = new Intent(this, MainService.class);
        stopService(intent);
        updateServiceStatus();
    }

    /**
     * Check if MainService is currently running and update UI accordingly
     */
    private void updateServiceStatus() {
        isServiceRunning = isServiceRunning(MainService.class);

        if (isServiceRunning) {
            btnToggleService.setText("Stop Service");
            tvServiceStatus.setText("Running");
            tvServiceStatus.setTextColor(0xFF00FF00); // Green
        } else {
            btnToggleService.setText("Start Service");
            tvServiceStatus.setText("Stopped");
            tvServiceStatus.setTextColor(0xFFFF0000); // Red
        }

        Log.d(TAG, "Service status updated: " + (isServiceRunning ? "Running" : "Stopped"));
    }

    /**
     * Update the last check timestamp display
     *
     * @param timestamp Unix timestamp in milliseconds
     */
    private void updateLastCheck(long timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String time = sdf.format(new Date(timestamp));
            tvLastCheck.setText(time);
        }
    }

    /**
     * Update sensor data display (TP2)
     *
     * @param sensorCount Total number of sensors tracked
     * @param lightsOnCount Number of sensors with lights detected as ON
     * @param status Status message from service
     * @param sensorDataJson JSON string containing detailed sensor information
     */
    private void updateSensorData(int sensorCount, int lightsOnCount, String status, String sensorDataJson) {
        Log.d(TAG, "updateSensorData() called with sensorCount=" + sensorCount +
                   ", lightsOnCount=" + lightsOnCount + ", status=" + status);

        StringBuilder sb = new StringBuilder();

        // Summary header
        sb.append("═══════════════════════════════\n");
        sb.append("📊 SUMMARY\n");
        sb.append("═══════════════════════════════\n");
        sb.append("Total Sensors: ").append(sensorCount).append("\n");
        sb.append("Lights ON: ").append(lightsOnCount).append("\n");
        sb.append("Status: ").append(status).append("\n\n");

        // Parse and display individual sensor details
        if (sensorDataJson != null && !sensorDataJson.isEmpty()) {
            try {
                org.json.JSONArray sensorsArray = new org.json.JSONArray(sensorDataJson);

                Log.d(TAG, "Parsed JSON array with " + sensorsArray.length() + " sensors");

                sb.append("═══════════════════════════════\n");
                sb.append("🔍 SENSOR DETAILS\n");
                sb.append("═══════════════════════════════\n\n");

                for (int i = 0; i < sensorsArray.length(); i++) {
                    org.json.JSONObject sensor = sensorsArray.getJSONObject(i);

                    String mote = sensor.optString("mote", "unknown");
                    String label = sensor.optString("label", "unknown");
                    double value = sensor.optDouble("value", 0.0);
                    long timestamp = sensor.optLong("timestamp", 0);
                    boolean lightOn = sensor.optBoolean("lightOn", false);

                    Log.d(TAG, "Sensor " + i + ": mote=" + mote + ", value=" + value + ", lightOn=" + lightOn);

                    // Format timestamp
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    String timeStr = sdf.format(new Date(timestamp));

                    // Format sensor entry with status indicator
                    String indicator = lightOn ? "💡 ON " : "🌙 OFF";
                    sb.append(indicator).append(" │ ").append(mote).append("\n");
                    sb.append("├─ Label: ").append(label).append("\n");
                    sb.append("├─ Value: ").append(String.format(Locale.getDefault(), "%.2f", value)).append("\n");
                    sb.append("└─ Time: ").append(timeStr).append("\n");

                    if (i < sensorsArray.length() - 1) {
                        sb.append("\n");
                    }
                }

            } catch (org.json.JSONException e) {
                Log.e(TAG, "Error parsing sensor details JSON", e);
                sb.append("\n⚠️ Error parsing sensor details\n");
            }
        } else {
            Log.w(TAG, "sensorDataJson is null or empty");
            sb.append("No sensor details available yet...\n");
        }

        String finalText = sb.toString();
        Log.d(TAG, "Setting tvSensorData text (length=" + finalText.length() + ")");
        Log.d(TAG, "First 200 chars: " + (finalText.length() > 200 ? finalText.substring(0, 200) : finalText));

        tvSensorData.setText(finalText);

        // Change text color based on lights detected
        if (lightsOnCount > 0) {
            tvSensorData.setTextColor(0xFFFF9800); // Orange - lights detected
        } else {
            tvSensorData.setTextColor(0xFF4CAF50); // Green - all clear
        }

        Log.d(TAG, "updateSensorData() completed successfully");
    }

    /**
     * Check if a specific service is running
     *
     * @param serviceClass The service class to check
     * @return true if service is running, false otherwise
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called - MainActivity is now visible");
        // Update service status when activity resumes
        updateServiceStatus();

        // If service is running, request an immediate update
        if (isServiceRunning) {
            Log.d(TAG, "Service is running - requesting immediate data update");
            Intent updateRequest = new Intent(this, MainService.class);
            updateRequest.setAction(MainService.ACTION_REQUEST_UPDATE);
            startService(updateRequest);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called - MainActivity going to background");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called - unregistering receiver");
        // Unregister broadcast receiver to prevent memory leaks
        if (serviceReceiver != null) {
            unregisterReceiver(serviceReceiver);
            serviceReceiver = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // Open SettingsActivity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

