package com.example.amio;

/**
 * SensorState - Data model representing the state of a light sensor
 *
 * Stores the current reading from a mote (sensor node) including:
 * - Mote identifier
 * - Last measured light value
 * - Timestamp of the measurement
 * - Computed state (light on/off based on delta hysteresis)
 *
 * TP2: Data model for sensor tracking and light detection with hysteresis
 *
 * Detection logic (delta-based hysteresis):
 * - Delta >= +25: light switches to ON
 * - Delta <= -25: light switches to OFF
 * - Delta in (-25, +25): state remains unchanged (prevents flickering)
 */
public class SensorState {

    private String mote;
    private String label;
    private double lastValue;
    private long timestamp;
    private boolean lightOn;

    /**
     * Default threshold for initial light detection (calibration value)
     * Used only for initial state determination
     */
    public static final double DEFAULT_LIGHT_THRESHOLD = 200.0;

    /**
     * Hysteresis delta thresholds
     * Positive delta to switch ON, negative delta to switch OFF
     */
    public static final double DELTA_ON = 25.0;
    public static final double DELTA_OFF = -25.0;

    public SensorState(String mote, String label, double lastValue, long timestamp) {
        this.mote = mote;
        this.label = label;
        this.lastValue = lastValue;
        this.timestamp = timestamp;
        // Initial state based on absolute threshold
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
     * Update sensor state with new reading using delta-based hysteresis.
     *
     * Hysteresis logic (prevents flickering):
     * - If delta >= +25: switch to ON
     * - If delta <= -25: switch to OFF
     * - If delta in (-25, +25): keep current state
     *
     * @param newValue New light sensor value
     * @param newTimestamp Timestamp of new reading
     * @param threshold Unused (kept for backward compatibility)
     * @return true if light state changed (off->on or on->off), false otherwise
     */
    public boolean updateState(double newValue, long newTimestamp, double threshold) {
        boolean wasLightOn = this.lightOn;

        // Calculate delta from previous value
        double delta = newValue - this.lastValue;

        // Apply hysteresis logic
        if (delta >= DELTA_ON) {
            // Significant increase -> turn ON
            this.lightOn = true;
        } else if (delta <= DELTA_OFF) {
            // Significant decrease -> turn OFF
            this.lightOn = false;
        }
        // else: delta in (-25, +25) -> state unchanged

        // Update stored values
        this.lastValue = newValue;
        this.timestamp = newTimestamp;

        // Return true if state changed
        return wasLightOn != this.lightOn;
    }

    /**
     * Check if this update represents a new light turned ON event.
     * Uses delta-based hysteresis detection.
     *
     * @param newValue New light sensor value
     * @param threshold Unused (kept for backward compatibility)
     * @return true if light was OFF and will switch to ON based on delta
     */
    public boolean detectNewLightOn(double newValue, double threshold) {
        // Calculate delta from current value
        double delta = newValue - this.lastValue;

        // Check if currently OFF and delta triggers ON
        return !this.lightOn && (delta >= DELTA_ON);
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
