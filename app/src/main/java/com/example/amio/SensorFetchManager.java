package com.example.amio;

import android.content.SharedPreferences;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class SensorFetchManager {
    private static final String TAG = "SensorFetchManager";
    // Default interval if not specified
    private static final long DEFAULT_FETCH_INTERVAL_MS = 5_000;

    private final MainService service;
    private final SharedPreferences prefs;
    private final double lightThreshold;
    private final NotificationHelper notificationHelper;
    private final FetchCallback callback;

    private Timer timer;
    private TimerTask task;

    public interface FetchCallback {
        void onFetched(String jsonResponse, String status);
    }

    public SensorFetchManager(MainService service,
                              SharedPreferences prefs,
                              double lightThreshold,
                              NotificationHelper notificationHelper,
                              FetchCallback callback) {
        this.service = service;
        this.prefs = prefs;
        this.lightThreshold = lightThreshold;
        this.notificationHelper = notificationHelper;
        this.callback = callback;
    }

    public void start(long intervalMs) {
        Log.d(TAG, "Starting periodic fetch task with interval: " + intervalMs + " ms");
        stop();
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                fetchDataFromServer();
            }
        };
        timer.scheduleAtFixedRate(task, 0, intervalMs > 0 ? intervalMs : DEFAULT_FETCH_INTERVAL_MS);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void fetchDataFromServer() {
        Log.d(TAG, "Fetching data from server...");
        String urlStr = prefs.getString("server_url", "http://37.59.110.9:8000/AMIO-API");
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
                callback.onFetched(jsonResponse, "Data fetched successfully");
            } else {
                Log.e(TAG, "HTTP request failed with code: " + responseCode);
                callback.onFetched(null, "HTTP Error: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from server", e);
            callback.onFetched(null, "Fetch error: " + e.getMessage());
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
}