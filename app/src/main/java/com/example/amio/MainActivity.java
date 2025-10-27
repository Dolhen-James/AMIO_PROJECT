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
public class MainActivity extends AppCompatActivity implements ServiceBroadcastCallback {
    // ...existing code...
    // Implement ServiceBroadcastCallback to decouple receiver from activity
    // @Override
    public void onServiceBroadcast(String status, long timestamp, int sensorCount, int lightsOnCount, String sensorDataJson) {
        updateLastCheck(timestamp);
        updateSensorData(sensorCount, lightsOnCount, status, sensorDataJson);
    }

    private static final String TAG = "MainActivity";

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
    protected void onCreate(Bundle savedInstanceState) { // Setup
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity created");


        // Set up the top app bar (Toolbar)
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Request notification permission for Android 13+ (API 33+) using helper
        NotificationHelper.requestNotificationPermission(this);

        // Initialize UI elements
        initializeViews();

        // Set up the toggle button
        setupToggleButton();

        // Check if service is already running
        updateServiceStatus();

        // Set up BroadcastReceiver for service updates
        setupBroadcastReceiver();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        NotificationHelper.handlePermissionResult(this, requestCode, permissions, grantResults);
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
    tvSensorData.setText(getString(R.string.sensor_no_data));
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
    serviceReceiver = new ServiceBroadcastReceiver(this);
    // Use callback interface for decoupling
    serviceReceiver = new ServiceBroadcastReceiver(this);

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

            // Update SharedPreferences so SettingsActivity reflects the change
            SettingsManager settingsManager = new SettingsManager(this);
            settingsManager.setBoolean("pref_service_enabled", true);
    }

    /**
     * Stop the MainService
     */
    private void stopService() {
        Log.d(TAG, "Stopping MainService");
        Intent intent = new Intent(this, MainService.class);
        stopService(intent);
        updateServiceStatus();

            // Update SharedPreferences so SettingsActivity reflects the change
            SettingsManager settingsManager = new SettingsManager(this);
            settingsManager.setBoolean("pref_service_enabled", false);
    }

    /**
     * Check if MainService is currently running and update UI accordingly
     */
    private void updateServiceStatus() {
        isServiceRunning = isServiceRunning(MainService.class);

        if (isServiceRunning) {
            btnToggleService.setText(getString(R.string.toggle_stop_service));
            tvServiceStatus.setText(getString(R.string.service_status_running));
            tvServiceStatus.setTextColor(getResources().getColor(R.color.service_running));
        } else {
            btnToggleService.setText(getString(R.string.toggle_start_service));
            tvServiceStatus.setText(getString(R.string.service_status_stopped));
            tvServiceStatus.setTextColor(getResources().getColor(R.color.service_stopped));
        }

        Log.d(TAG, "Service status updated: " + (isServiceRunning ? "Running" : "Stopped"));
    }

    /**
     * Update the last check timestamp display
     *
     * @param timestamp Unix timestamp in milliseconds
     */
    public void updateLastCheck(long timestamp) {
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
    public void updateSensorData(int sensorCount, int lightsOnCount, String status, String sensorDataJson) {
        Log.d(TAG, "updateSensorData() called with sensorCount=" + sensorCount +
                ", lightsOnCount=" + lightsOnCount + ", status=" + status);

        StringBuilder sb = new StringBuilder();
        sb.append(SensorDataHelper.formatSensorSummary(sensorCount, lightsOnCount, status));

        try {
            java.util.List<SensorDataHelper.SensorInfo> sensors = SensorDataHelper.parseSensorData(sensorDataJson);
            sb.append(SensorDataHelper.formatSensorDetails(sensors));
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error parsing sensor details JSON", e);
            sb.append("\n⚠️ Error parsing sensor details\n");
        }

        String finalText = sb.toString();
        Log.d(TAG, "Setting tvSensorData text (length=" + finalText.length() + ")");
        Log.d(TAG, "First 200 chars: " + (finalText.length() > 200 ? finalText.substring(0, 200) : finalText));

        tvSensorData.setText(finalText);

        // Change text color based on lights detected
        if (lightsOnCount > 0) {
            tvSensorData.setTextColor(getResources().getColor(R.color.sensor_lights_detected));
        } else {
            tvSensorData.setTextColor(getResources().getColor(R.color.sensor_all_clear));
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

