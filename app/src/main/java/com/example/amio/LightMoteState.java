package com.example.amio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LightMoteState - Represents the state of a specific mote for a specific light
 *
 * Since the same mote can be used for multiple lights with different values,
 * we need to track each (light, mote) combination separately.
 */
public class LightMoteState {
    private final String lightLabel;  // e.g., "light1"
    private final String moteId;      // e.g., "9.138"
    private double currentValue;
    private double previousValue;
    private long lastUpdated;
    private boolean isLightOn;
    private boolean hasBeenUpdated;  // Cela sert au démarrage pour savoir si on a une valeur précédente ou pas

    // Threshold for initial detection (when no previous value exists)
    public static final double INITIAL_VALUE_THRESHOLD = 250.0;

    // Threshold for change detection (difference between current and previous)
    public static final double LIGHT_CHANGE_THRESHOLD = 50.0;

    public LightMoteState(String lightLabel, String moteId, double initialValue, long timestamp) {
        this.lightLabel = lightLabel;
        this.moteId = moteId;
        this.currentValue = initialValue;
        this.previousValue = initialValue;
        this.lastUpdated = timestamp;
        this.hasBeenUpdated = false;  // No real previous value yet

        // Initialize light status based on INITIAL threshold (absolute value)
        this.isLightOn = initialValue > INITIAL_VALUE_THRESHOLD;
    }

    /**
     * Update the state with a new value and check for light changes
     *
     * Logic:
     * - FIRST UPDATE (no previous value): Use absolute threshold (250 lux)
     *   - ON if value > 250
     *   - OFF if value <= 250
     *
     * - SUBSEQUENT UPDATES (has previous value): Use change threshold (±50 lux)
     *   - Turn ON if: currentValue - previousValue > 50
     *   - Turn OFF if: currentValue - previousValue < -50
     *   - Otherwise: keep current state
     *
     * @param newValue The new sensor value
     * @param timestamp The timestamp of the reading
     * @return true if the light status changed, false otherwise
     */
    public boolean updateState(double newValue, long timestamp) {
        boolean wasOn = this.isLightOn;

        this.previousValue = this.currentValue;
        this.currentValue = newValue;
        this.lastUpdated = timestamp;

        if (!hasBeenUpdated) {
            // First update: use absolute threshold
            this.isLightOn = newValue > INITIAL_VALUE_THRESHOLD;
            this.hasBeenUpdated = true;
        } else {
            // Subsequent updates: use change threshold (difference)
            double difference = newValue - previousValue;

            if (difference > LIGHT_CHANGE_THRESHOLD) {
                // Significant increase -> light turned ON
                this.isLightOn = true;
            } else if (difference < -LIGHT_CHANGE_THRESHOLD) {
                // Significant decrease -> light turned OFF
                this.isLightOn = false;
            }
            // else: no significant change, keep current state
        }

        // Return true if the light status changed
        return wasOn != this.isLightOn;
    }

    public String getLightLabel() {
        return lightLabel;
    }

    public String getMoteId() {
        return moteId;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public boolean isLightOn() {
        return isLightOn;
    }



    /**
     * Set the light status directly (used when parsing from JSON)
     */
    public void setLightOnStatus(boolean lightOn) {
        this.isLightOn = lightOn;
    }

    /**
     * Create a unique key from light label and mote ID
     */
    public static String createKey(String lightLabel, String moteId) {
        return lightLabel + "_" + moteId;
    }

    // ========== Static utility methods for parsing and formatting ==========

    /**
     * Parse JSON sensor data into a list of LightMoteState objects
     */
    public static List<LightMoteState> parseFromJson(String sensorDataJson) throws JSONException {
        List<LightMoteState> sensors = new ArrayList<>();
        if (sensorDataJson == null || sensorDataJson.isEmpty()) return sensors;
        JSONArray sensorsArray = new JSONArray(sensorDataJson);
        for (int i = 0; i < sensorsArray.length(); i++) {
            JSONObject sensor = sensorsArray.getJSONObject(i);
            String light = sensor.optString("light", "unknown");
            String mote = sensor.optString("mote", "unknown");
            double value = sensor.optDouble("value", 0.0);
            long timestamp = sensor.optLong("timestamp", 0);
            boolean lightOn = sensor.optBoolean("lightOn", false);

            // Create LightMoteState from JSON data
            LightMoteState state = new LightMoteState(light, mote, value, timestamp);
            state.setLightOnStatus(lightOn);
            sensors.add(state);
        }
        return sensors;
    }

    /**
     * Format a summary of sensor data
     */
    public static String formatSummary(int lightsOnCount, String status, List<String> fetchErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("📊 SUMMARY\n");
        sb.append("═══════════════════════════════\n");
        sb.append("Lights ON: ").append(lightsOnCount).append("\n");
        sb.append("Status: ").append(status).append("\n");

        // Display fetch errors if any
        if (fetchErrors != null && !fetchErrors.isEmpty()) {
            sb.append("\n⚠️ FETCH ERRORS:\n");
            for (String error : fetchErrors) {
                sb.append("  ❌ ").append(error).append("\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Format detailed sensor information grouped by light
     */
    public static String formatDetails(List<LightMoteState> sensors) {
        if (sensors.isEmpty()) {
            return "No sensor details available yet...\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("🔍 SENSOR DETAILS\n");
        sb.append("═══════════════════════════════\n\n");
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        // Group sensors by light
        for (String lightLabel : new String[]{"light1", "light2"}) {
            List<LightMoteState> lightSensors = new ArrayList<>();
            for (LightMoteState sensor : sensors) {
                if (sensor.getLightLabel().equals(lightLabel)) {
                    lightSensors.add(sensor);
                }
            }

            if (!lightSensors.isEmpty()) {
                sb.append("──────────────────────────────\n");
                sb.append("💡 ").append(lightLabel.toUpperCase()).append("\n");
                sb.append("──────────────────────────────\n");

                for (int i = 0; i < lightSensors.size(); i++) {
                    LightMoteState sensor = lightSensors.get(i);
                    String timeStr = sdf.format(new Date(sensor.getLastUpdated()));
                    String indicator = sensor.isLightOn() ? "💡 ON " : "🌙 OFF";
                    sb.append(indicator).append(" │ Mote ").append(sensor.getMoteId()).append("\n");
                    sb.append("├─ Value: ").append(String.format(Locale.getDefault(), "%.2f lux", sensor.getCurrentValue())).append("\n");
                    sb.append("└─ Time: ").append(timeStr).append("\n");
                    if (i < lightSensors.size() - 1) {
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}

