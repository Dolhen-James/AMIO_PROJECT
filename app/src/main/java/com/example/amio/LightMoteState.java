package com.example.amio;

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

    public static final double DEFAULT_LIGHT_THRESHOLD = 50.0;

    public LightMoteState(String lightLabel, String moteId, double initialValue, long timestamp) {
        this.lightLabel = lightLabel;
        this.moteId = moteId;
        this.currentValue = initialValue;
        this.previousValue = initialValue;
        this.lastUpdated = timestamp;
        this.isLightOn = false;
    }

    /**
     * Update the state with a new value and check for light changes
     * @param newValue The new sensor value
     * @param timestamp The timestamp of the reading
     * @param threshold The threshold for detecting light changes
     * @return true if the light status changed, false otherwise
     */
    public boolean updateState(double newValue, long timestamp, double threshold) {
        this.previousValue = this.currentValue;
        this.currentValue = newValue;
        this.lastUpdated = timestamp;

        double difference = Math.abs(newValue - previousValue);
        boolean wasOn = this.isLightOn;

        if (difference >= threshold) {
            // Significant change detected
            this.isLightOn = (newValue > previousValue);
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

    public double getPreviousValue() {
        return previousValue;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public boolean isLightOn() {
        return isLightOn;
    }

    /**
     * Get a unique key for this light-mote combination
     */
    public String getUniqueKey() {
        return lightLabel + "_" + moteId;
    }

    /**
     * Create a unique key from light label and mote ID
     */
    public static String createKey(String lightLabel, String moteId) {
        return lightLabel + "_" + moteId;
    }
}

