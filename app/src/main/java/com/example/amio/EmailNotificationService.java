package com.example.amio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * EmailNotificationService - Manages email notifications via HTTP POST
 *
 * This service handles sending HTTP POST notifications for sensor light changes,
 * respecting the same scheduling preferences as in-app notifications
 * (enabled/disabled, allowed days, time range).
 *
 * Sends a POST request to http://peniche.pakbo-et-lombtrik:8000 (which will send the mail) with:
 * - recipientEmail: recipient email address
 * - motesOn: list of motes with light ON
 * - motesOff: list of motes with light OFF
 */
public class EmailNotificationService {

    private static final String TAG = "EmailNotificationService";
    private static final String SERVER_URL = "http://peniche.pakbo-et-lombrik.fr:8000/notify";

    private final SharedPreferences prefs;
    private final ExecutorService executorService;

    public EmailNotificationService(Context context) {
        this.prefs = context.getSharedPreferences("amio_settings", Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Check if email notifications are properly configured
     *
     * @return true if recipient email is set
     */
    public boolean isConfigured() {
        String recipientEmail = prefs.getString("pref_email_recipient", "");
        return recipientEmail != null && !recipientEmail.isEmpty();
    }

    /**
     * Send a grouped email notification showing all lights that changed state
     *
     * @param motesOn  List of motes that just turned ON
     * @param motesOff List of motes that just turned OFF
     */
    public void sendGroupedEmailNotification(List<String> motesOn, List<String> motesOff) {
        Log.d(TAG, "sendGroupedEmailNotification() - ON: " + motesOn.size() + ", OFF: " + motesOff.size());
        Log.d(TAG, "Motes ON: " + motesOn);
        Log.d(TAG, "Motes OFF: " + motesOff);

        // Check if email notifications are enabled in preferences
        boolean emailEnabled = prefs.getBoolean("pref_email_notifications_enabled", false);
        Log.d(TAG, "Email notifications enabled in prefs: " + emailEnabled);
        if (!emailEnabled) {
            Log.d(TAG, "Email notifications disabled in preferences - skipping email");
            return;
        }

        // Check if credentials are configured
        if (!isConfigured()) {
            Log.d(TAG, "Email notifications not configured (missing recipient email) - skipping email");
            return;
        }

        // Check if recipient email is set
        String recipientEmail = prefs.getString("pref_email_recipient", "");
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            Log.d(TAG, "No recipient email address configured - skipping email");
            return;
        }

        // Check if today is in allowed email notification days
        Set<String> allowedDays = prefs.getStringSet("pref_email_notification_days", null);
        if (allowedDays == null || allowedDays.isEmpty()) {
            Log.d(TAG, "No email notification days set - skipping email");
            return;
        }

        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        String today = null;
        switch (dayOfWeek) {
            case Calendar.MONDAY: today = "Mon"; break;
            case Calendar.TUESDAY: today = "Tue"; break;
            case Calendar.WEDNESDAY: today = "Wed"; break;
            case Calendar.THURSDAY: today = "Thu"; break;
            case Calendar.FRIDAY: today = "Fri"; break;
            case Calendar.SATURDAY: today = "Sat"; break;
            case Calendar.SUNDAY: today = "Sun"; break;
        }
        if (today == null || !allowedDays.contains(today)) {
            Log.d(TAG, "Today (" + today + ") not in allowed email notification days - skipping email");
            return;
        }

        // Check if current time is in allowed range
        String timeRange = prefs.getString("pref_email_notification_time_range", "08:00-20:00");
        Log.d(TAG, "Email time range check: raw value from SharedPreferences: " + timeRange);
        String[] parts = timeRange.split("-");
        if (parts.length != 2) {
            Log.d(TAG, "Email time range check: invalid format: " + timeRange);
            return;
        }

        int startHour = 8, startMinute = 0, endHour = 20, endMinute = 0;
        try {
            String[] startParts = parts[0].split(":");
            String[] endParts = parts[1].split(":");
            startHour = Integer.parseInt(startParts[0]);
            startMinute = Integer.parseInt(startParts[1]);
            endHour = Integer.parseInt(endParts[0]);
            endMinute = Integer.parseInt(endParts[1]);
            Log.d(TAG, "Email time range check: start=" + startHour + ":" + startMinute + ", end=" + endHour + ":" + endMinute);
        } catch (Exception e) {
            Log.d(TAG, "Email time range check: error parsing time range: " + timeRange);
            return;
        }

        int nowHour = calendar.get(Calendar.HOUR_OF_DAY);
        int nowMinute = calendar.get(Calendar.MINUTE);
        int now = nowHour * 60 + nowMinute;
        int start = startHour * 60 + startMinute;
        int end = endHour * 60 + endMinute;

        boolean inRange;
        if (start <= end) {
            inRange = (now >= start && now <= end);
        } else {
            // Overnight range (e.g., 22:00-06:00)
            inRange = (now >= start || now <= end);
        }

        Log.d(TAG, "Email time range check: now=" + nowHour + ":" + nowMinute + ", inRange=" + inRange);
        if (!inRange) {
            Log.d(TAG, "Current time not in allowed email notification range (" + timeRange + ") - skipping email");
            return;
        }

        Log.d(TAG, "Preparing to send email notification to: " + recipientEmail);
        // Send HTTP notification asynchronously
        sendHttpNotificationAsync(recipientEmail, motesOn, motesOff);

        Log.d(TAG, "HTTP notification scheduled to be sent");
    }

    /**
     * Send HTTP POST notification asynchronously
     */
    private void sendHttpNotificationAsync(String recipientEmail, List<String> motesOn, List<String> motesOff) {
        executorService.execute(() -> {
            try {
                sendHttpNotification(recipientEmail, motesOn, motesOff);
                Log.i(TAG, "HTTP notification sent successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send HTTP notification", e);
            }
        });
    }

    /**
     * Send HTTP POST request to notification server
     *
     * Sends a POST request to SERVER_URL with motesOn and motesOff parameters.
     * Must be called from a background thread.
     */
    private void sendHttpNotification(String recipientEmail, List<String> motesOn, List<String> motesOff) throws Exception {
        Log.d(TAG, "Sending HTTP POST to: " + SERVER_URL);

        // Convert lists to comma-separated strings
        String recipientEmailParam = recipientEmail;
        String motesOnParam = String.join(",", motesOn);
        String motesOffParam = String.join(",", motesOff);

        Log.d(TAG, "motesOn: " + motesOnParam);
        Log.d(TAG, "motesOff: " + motesOffParam);

        // Create OkHttp client
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        // Build form data
        FormBody formBody = new FormBody.Builder()
                .add("recipientEmail", recipientEmailParam)
                .add("motesOn", motesOnParam)
                .add("motesOff", motesOffParam)
                .build();

        // Create request
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(formBody)
                .build();

        // Execute request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP POST failed: " + response.code() + " - " + responseBody);
                throw new Exception("HTTP POST failed: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            Log.i(TAG, "HTTP POST successful: " + response.code() + " - " + responseBody);
        }
    }

    /**
     * Shutdown the executor service when no longer needed
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
