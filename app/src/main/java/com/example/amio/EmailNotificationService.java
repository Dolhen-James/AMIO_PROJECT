package com.example.amio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EmailNotificationService - Manages email notifications via NotificationAPI SDK
 *
 * This service handles sending email notifications for sensor light changes,
 * respecting the same scheduling preferences as in-app notifications
 * (enabled/disabled, allowed days, time range).
 *
 * Configuration requires NotificationAPI client ID and client secret to be set
 * in app settings.
 */
public class EmailNotificationService {

    private static final String TAG = "EmailNotificationService";

    private final SharedPreferences prefs;
    private final ExecutorService executorService;

    // NotificationAPI credentials
    private String clientId;
    private String clientSecret;

    public EmailNotificationService(Context context) {
        this.prefs = context.getSharedPreferences("amio_settings", Context.MODE_PRIVATE);
        this.executorService = Executors.newSingleThreadExecutor();

        // Load credentials from preferences
        loadCredentials();
    }

    /**
     * Load NotificationAPI credentials from SharedPreferences
     */
    private void loadCredentials() {
        this.clientId = prefs.getString("pref_email_client_id", "");
        this.clientSecret = prefs.getString("pref_email_client_secret", "");
    }

    /**
     * Check if email notifications are properly configured
     *
     * @return true if client ID and secret are set
     */
    public boolean isConfigured() {
        loadCredentials();
        return clientId != null && !clientId.isEmpty() &&
               clientSecret != null && !clientSecret.isEmpty();
    }

    /**
     * Send a grouped email notification showing all lights that changed state
     *
     * @param motesOn  List of motes that just turned ON
     * @param motesOff List of motes that just turned OFF
     */
    public void sendGroupedEmailNotification(List<String> motesOn, List<String> motesOff) {
        Log.d(TAG, "sendGroupedEmailNotification() - ON: " + motesOn.size() + ", OFF: " + motesOff.size());

        // Check if email notifications are enabled in preferences
        boolean emailEnabled = prefs.getBoolean("pref_email_notifications_enabled", false);
        if (!emailEnabled) {
            Log.d(TAG, "Email notifications disabled in preferences - skipping email");
            return;
        }

        // Check if credentials are configured
        if (!isConfigured()) {
            Log.d(TAG, "Email notifications not configured (missing client ID or secret) - skipping email");
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

        // Build email content
        String subject = buildEmailSubject(motesOn, motesOff);
        String htmlContent = buildEmailHtmlContent(motesOn, motesOff);

        // Send email asynchronously
        sendEmailAsync(recipientEmail, subject, htmlContent);
    }

    /**
     * Build email subject based on changes
     */
    private String buildEmailSubject(List<String> motesOn, List<String> motesOff) {
        int totalChanges = motesOn.size() + motesOff.size();

        if (totalChanges == 1) {
            if (!motesOn.isEmpty()) {
                return "AMIO - Lumière allumée: " + motesOn.get(0);
            } else {
                return "AMIO - Lumière éteinte: " + motesOff.get(0);
            }
        } else {
            return "AMIO - " + totalChanges + " changements détectés";
        }
    }

    /**
     * Build HTML content for the email body
     */
    private String buildEmailHtmlContent(List<String> motesOn, List<String> motesOff) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>AMIO - Alerte Capteurs</h2>");

        if (!motesOn.isEmpty()) {
            html.append("<h3 style='color: #ff9800;'>💡 LUMIÈRES ALLUMÉES</h3>");
            html.append("<ul>");
            for (String mote : motesOn) {
                html.append("<li>").append(mote).append("</li>");
            }
            html.append("</ul>");
        }

        if (!motesOff.isEmpty()) {
            html.append("<h3 style='color: #4caf50;'>🌙 LUMIÈRES ÉTEINTES</h3>");
            html.append("<ul>");
            for (String mote : motesOff) {
                html.append("<li>").append(mote).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<p><small>Cet email a été envoyé automatiquement par l'application AMIO.</small></p>");
        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Send email asynchronously using NotificationAPI
     */
    private void sendEmailAsync(String recipientEmail, String subject, String htmlContent) {
        executorService.execute(() -> {
            try {
                sendEmailViaNotificationApi(recipientEmail, subject, htmlContent);
                Log.i(TAG, "Email sent successfully to: " + recipientEmail);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send email", e);
            }
        });
    }

    /**
     * Send email using NotificationAPI SDK
     *
     * This method performs the actual API call to NotificationAPI service.
     * Must be called from a background thread.
     */
    private void sendEmailViaNotificationApi(String recipientEmail, String subject, String htmlContent) throws Exception {
        // Reload credentials in case they changed
        loadCredentials();

        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("NotificationAPI credentials not configured");
        }

        Log.d(TAG, "Sending email via NotificationAPI to: " + recipientEmail);

        // Initialize NotificationAPI client
        com.notificationapi.NotificationApi api = new com.notificationapi.NotificationApi(clientId, clientSecret);

        // Create user with email
        com.notificationapi.model.User user = new com.notificationapi.model.User(recipientEmail)
                .setEmail(recipientEmail);

        // Create notification request with email options
        com.notificationapi.model.NotificationRequest request = new com.notificationapi.model.NotificationRequest("mote_update", user)
                .setEmail(new com.notificationapi.model.EmailOptions()
                        .setSubject(subject)
                        .setHtml(htmlContent));

        // Send the notification
        api.send(request);

        Log.i(TAG, "NotificationAPI request sent successfully");
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
