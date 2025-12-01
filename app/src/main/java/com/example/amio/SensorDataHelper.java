package com.example.amio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SensorDataHelper {
    public static class SensorInfo {
        public String light;     // e.g., "light1"
        public String mote;      // e.g., "9.138"
        public double value;
        public long timestamp;
        public boolean lightOn;

        public SensorInfo(String light, String mote, double value, long timestamp, boolean lightOn) {
            this.light = light;
            this.mote = mote;
            this.value = value;
            this.timestamp = timestamp;
            this.lightOn = lightOn;
        }
    }

    public static List<SensorInfo> parseSensorData(String sensorDataJson) throws JSONException {
        List<SensorInfo> sensors = new ArrayList<>();
        if (sensorDataJson == null || sensorDataJson.isEmpty()) return sensors;
        JSONArray sensorsArray = new JSONArray(sensorDataJson);
        for (int i = 0; i < sensorsArray.length(); i++) {
            JSONObject sensor = sensorsArray.getJSONObject(i);
            String light = sensor.optString("light", "unknown");
            String mote = sensor.optString("mote", "unknown");
            double value = sensor.optDouble("value", 0.0);
            long timestamp = sensor.optLong("timestamp", 0);
            boolean lightOn = sensor.optBoolean("lightOn", false);
            sensors.add(new SensorInfo(light, mote, value, timestamp, lightOn));
        }
        return sensors;
    }

    public static String formatSensorSummary(int sensorCount, int lightsOnCount, String status, List<String> fetchErrors) {
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

    public static String formatSensorDetails(List<SensorInfo> sensors) {
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
            List<SensorInfo> lightSensors = new ArrayList<>();
            for (SensorInfo sensor : sensors) {
                if (sensor.light.equals(lightLabel)) {
                    lightSensors.add(sensor);
                }
            }

            if (!lightSensors.isEmpty()) {
                sb.append("──────────────────────────────\n");
                sb.append("💡 ").append(lightLabel.toUpperCase()).append("\n");
                sb.append("──────────────────────────────\n");

                for (int i = 0; i < lightSensors.size(); i++) {
                    SensorInfo sensor = lightSensors.get(i);
                    String timeStr = sdf.format(new Date(sensor.timestamp));
                    String indicator = sensor.lightOn ? "💡 ON " : "🌙 OFF";
                    sb.append(indicator).append(" │ Mote ").append(sensor.mote).append("\n");
                    sb.append("├─ Value: ").append(String.format(Locale.getDefault(), "%.2f lux", sensor.value)).append("\n");
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
