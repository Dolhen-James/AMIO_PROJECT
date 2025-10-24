package com.example.amio;

/**
 * SensorState - Data model representing the state of a light sensor
 *
 * Stores the current reading from a mote (sensor node) including:
 * - Mote identifier
 * - Last measured light value
 * - Timestamp of the measurement
 * - Computed state (light on/off based on threshold)
 *
 * TP2: Data model for sensor tracking and light detection
 */
public class SensorState {

    private String mote;
    private String label;
    private double lastValue;
    private long timestamp;
    private boolean lightOn;

    /**
     * Default threshold for light detection (calibration value)
     * Values above this threshold indicate light is ON
     * TP2: This value should be calibrated based on actual sensor data
     */
    public static final double DEFAULT_LIGHT_THRESHOLD = 200.0;

    public SensorState(String mote, String label, double lastValue, long timestamp) {
        this.mote = mote;
        this.label = label;
        this.lastValue = lastValue;
        this.timestamp = timestamp;
        this.lightOn = lastValue > DEFAULT_LIGHT_THRESHOLD;
    }

    // Getters
    public String getMote() {
        return mote;
    }

    public String getLabel() {
        return label;
    }

    public double getLastValue() {
        return lastValue;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isLightOn() {
        return lightOn;
    }

    /**
     * Get value for UI display (same as lastValue)
     */
    public double getValue() {
        return lastValue;
    }

    /**
     * Get last updated timestamp (same as timestamp)
     */
    public long getLastUpdated() {
        return timestamp;
    }

    // Setters
    public void setLastValue(double lastValue) {
        this.lastValue = lastValue;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setLightOn(boolean lightOn) {
        this.lightOn = lightOn;
    }

    /**
     * Update sensor state with new reading and detect if light status changed
     *
     * @param newValue New light sensor value
     * @param newTimestamp Timestamp of new reading
     * @param threshold Light detection threshold
     * @return true if light state changed (off->on or on->off), false otherwise
     */
    public boolean updateState(double newValue, long newTimestamp, double threshold) {
        boolean wasLightOn = this.lightOn;
        this.lastValue = newValue;
        this.timestamp = newTimestamp;
        this.lightOn = newValue > threshold;

        // Return true if state changed
        return wasLightOn != this.lightOn;
    }

    /**
     * Check if this update represents a new light turned ON event
     *
     * @param newValue New light sensor value
     * @param threshold Light detection threshold
     * @return true if light was OFF and is now ON
     */
    public boolean detectNewLightOn(double newValue, double threshold) {
        boolean newLightOn = newValue > threshold;
        return !this.lightOn && newLightOn;
    }

    @Override
    public String toString() {
        return "SensorState{" +
                "mote='" + mote + '\'' +
                ", label='" + label + '\'' +
                ", lastValue=" + lastValue +
                ", timestamp=" + timestamp +
                ", lightOn=" + lightOn +
                '}';
    }
}
