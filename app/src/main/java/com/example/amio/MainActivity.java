package com.example.amio;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
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
 *
 * TP1: Basic service control and status display
 * TP3: BroadcastReceiver for service communication
 */
public class MainActivity extends AppCompatActivity {

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity created");

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
     * Initialize all UI elements by finding them by ID
     */
    private void initializeViews() {
        btnToggleService = findViewById(R.id.btnToggleService);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvLastCheck = findViewById(R.id.tvLastCheck);
        tvSensorData = findViewById(R.id.tvSensorData);
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
                if (MainService.ACTION_RESULT.equals(intent.getAction())) {
                    // Extract data from broadcast
                    String status = intent.getStringExtra(MainService.EXTRA_STATUS);
                    long timestamp = intent.getLongExtra(MainService.EXTRA_TIMESTAMP, 0);

                    // Update UI on main thread
                    updateLastCheck(timestamp);

                    Log.d(TAG, "Received broadcast from service: " + status);

                    // TODO TP2: Update sensor data display
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
        // Update service status when activity resumes
        updateServiceStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver to prevent memory leaks
        if (serviceReceiver != null) {
            unregisterReceiver(serviceReceiver);
        }
    }
}