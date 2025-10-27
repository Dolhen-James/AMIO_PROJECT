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
        public String mote;
        public String label;
        public double value;
        public long timestamp;
        public boolean lightOn;

        public SensorInfo(String mote, String label, double value, long timestamp, boolean lightOn) {
            this.mote = mote;
            this.label = label;
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
            String mote = sensor.optString("mote", "unknown");
            String label = sensor.optString("label", "unknown");
            double value = sensor.optDouble("value", 0.0);
            long timestamp = sensor.optLong("timestamp", 0);
            boolean lightOn = sensor.optBoolean("lightOn", false);
            sensors.add(new SensorInfo(mote, label, value, timestamp, lightOn));
        }
        return sensors;
    }

    public static String formatSensorSummary(int sensorCount, int lightsOnCount, String status) {
        return "═══════════════════════════════\n" +
                "📊 SUMMARY\n" +
                "═══════════════════════════════\n" +
                "Total Sensors: " + sensorCount + "\n" +
                "Lights ON: " + lightsOnCount + "\n" +
                "Status: " + status + "\n\n";
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
        for (int i = 0; i < sensors.size(); i++) {
            SensorInfo sensor = sensors.get(i);
            String timeStr = sdf.format(new Date(sensor.timestamp));
            String indicator = sensor.lightOn ? "💡 ON " : "🌙 OFF";
            sb.append(indicator).append(" │ ").append(sensor.mote).append("\n");
            sb.append("├─ Label: ").append(sensor.label).append("\n");
            sb.append("├─ Value: ").append(String.format(Locale.getDefault(), "%.2f", sensor.value)).append("\n");
            sb.append("└─ Time: ").append(timeStr).append("\n");
            if (i < sensors.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
