package com.example.amio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NotificationHelper - Manages all notification-related functionality
 *
 * Handles:
 * - Notification channel creation
 * - Grouped notifications for light state changes
 * - Notification cooldown to prevent spam
 * - Device vibration on notifications
 *
 * TP2: Centralized notification management
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    // Notification configuration
    private static final String CHANNEL_ID = "amio_alerts_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 1000; // 5 seconds

    private final Context context;
    private final SharedPreferences prefs;
    private final ConcurrentHashMap<String, Long> lastNotificationTime = new ConcurrentHashMap<>();

    public NotificationHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("amio_prefs", Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    /**
     * Create notification channel for API >= 26 (Oreo+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AMIO Alerts";
            String description = "Notifications for detected lights left on";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + CHANNEL_ID);
            } else {
                Log.w(TAG, "NotificationManager is null - cannot create channel");
            }
        }
    }

    /**
     * Send a grouped notification showing all lights that just changed state
     *
     * @param motesOn List of motes that just turned ON
     * @param motesOff List of motes that just turned OFF
     */
    public void sendGroupedNotification(List<String> motesOn, List<String> motesOff) {
        Log.d(TAG, "sendGroupedNotification() - ON: " + motesOn.size() + ", OFF: " + motesOff.size());

        // Check if notifications are enabled in preferences
        boolean notificationsEnabled = prefs.getBoolean("pref_notifications_enabled", true);
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications disabled in preferences - skipping notification");
            return;
        }

        // Cooldown check - use a global key for grouped notifications
        String cooldownKey = "grouped_notification";
        if (!checkCooldown(cooldownKey)) {
            return;
        }

        // Build notification content
        String title = buildNotificationTitle(motesOn, motesOff);
        String text = buildNotificationText(motesOn, motesOff);
        String bigText = buildNotificationBigText(motesOn, motesOff);

        // Create pending intent to open MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(alarmSound)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Send notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                updateCooldown(cooldownKey);
                vibrateDevice(200);
                Log.i(TAG, "Grouped notification posted (ON:" + motesOn.size() + ", OFF:" + motesOff.size() + ")");
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted");
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            updateCooldown(cooldownKey);
            vibrateDevice(200);
            Log.i(TAG, "Grouped notification posted (ON:" + motesOn.size() + ", OFF:" + motesOff.size() + ")");
        }
    }

    /**
     * Build notification title based on changes
     */
    private String buildNotificationTitle(List<String> motesOn, List<String> motesOff) {
        int totalChanges = motesOn.size() + motesOff.size();

        if (totalChanges == 1) {
            if (!motesOn.isEmpty()) {
                return "💡 Lumière allumée: " + motesOn.get(0);
            } else {
                return "🌙 Lumière éteinte: " + motesOff.get(0);
            }
        } else {
            return "🔄 " + totalChanges + " changements détectés";
        }
    }

    /**
     * Build compact notification text for collapsed view
     */
    private String buildNotificationText(List<String> motesOn, List<String> motesOff) {
        StringBuilder text = new StringBuilder();

        if (!motesOn.isEmpty()) {
            text.append("💡 ALLUMÉES: ");
            for (int i = 0; i < motesOn.size(); i++) {
                text.append(motesOn.get(i));
                if (i < motesOn.size() - 1) {
                    text.append(", ");
                }
            }
        }

        if (!motesOff.isEmpty()) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append("🌙 ÉTEINTES: ");
            for (int i = 0; i < motesOff.size(); i++) {
                text.append(motesOff.get(i));
                if (i < motesOff.size() - 1) {
                    text.append(", ");
                }
            }
        }

        return text.toString();
    }

    /**
     * Build detailed notification text for expanded view
     */
    private String buildNotificationBigText(List<String> motesOn, List<String> motesOff) {
        StringBuilder bigText = new StringBuilder();

        if (!motesOn.isEmpty()) {
            bigText.append("💡 LUMIÈRES ALLUMÉES:\n");
            for (String mote : motesOn) {
                bigText.append("  • ").append(mote).append("\n");
            }
        }

        if (!motesOff.isEmpty()) {
            if (bigText.length() > 0) {
                bigText.append("\n");
            }
            bigText.append("🌙 LUMIÈRES ÉTEINTES:\n");
            for (String mote : motesOff) {
                bigText.append("  • ").append(mote).append("\n");
            }
        }

        return bigText.toString();
    }

    /**
     * Check if enough time has passed since last notification (cooldown)
     *
     * @param key Cooldown key
     * @return true if notification can be sent, false if still in cooldown
     */
    private boolean checkCooldown(String key) {
        Long lastTime = lastNotificationTime.get(key);
        long currentTime = System.currentTimeMillis();

        if (lastTime != null && (currentTime - lastTime) < NOTIFICATION_COOLDOWN_MS) {
            long remainingCooldown = (NOTIFICATION_COOLDOWN_MS - (currentTime - lastTime)) / 1000;
            Log.d(TAG, "Notification cooldown active - wait " + remainingCooldown + " seconds");
            return false;
        }

        return true;
    }

    /**
     * Update cooldown timestamp for a given key
     */
    private void updateCooldown(String key) {
        lastNotificationTime.put(key, System.currentTimeMillis());
    }

    /**
     * Vibrate device briefly
     *
     * @param millis Duration in milliseconds
     */
    private void vibrateDevice(long millis) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(millis,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(millis);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Vibration failed", e);
        }
    }

    /**
     * Send email intent for critical alerts
     *
     * @param mote Sensor identifier
     * @param label Sensor label
     * @param value Sensor value
     * @param timestamp Detection timestamp
     * @param emailAddress Recipient email address
     */
    public void sendEmailIntent(String mote, String label, double value, long timestamp, String emailAddress) {
        Log.d(TAG, "sendEmailIntent() mote=" + mote);

        String subject = "AMIO - Lumière détectée: " + mote;
        String body = String.format(java.util.Locale.getDefault(),
            "Capteur %s (%s) détecte une lumière à %d (val=%.2f)", mote, label, timestamp, value);

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        if (emailAddress != null && !emailAddress.isEmpty()) {
            emailIntent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + Uri.encode(emailAddress)));
        }
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(emailIntent, "Envoyer un email..."));
            Log.i(TAG, "Email chooser launched");
        } catch (Exception e) {
            Log.e(TAG, "No email client available", e);
        }
    }
}
