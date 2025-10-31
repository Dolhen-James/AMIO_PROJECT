package com.example.amio;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

public class TimeRangePreference extends DialogPreference {
    private int startHour = 18, startMinute = 0, endHour = 23, endMinute = 0;
    private TimePicker startPicker, endPicker;

    public TimeRangePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.pref_time_range_dialog);
        setPositiveButtonText("OK");
        setNegativeButtonText("Cancel");
        // Set initial summary to default value
        setSummary("From " + String.format("%02d:%02d", startHour, startMinute) + " to " + String.format("%02d:%02d", endHour, endMinute));
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        startPicker = view.findViewById(R.id.startTimePicker);
        endPicker = view.findViewById(R.id.endTimePicker);
        startPicker.setIs24HourView(true);
        endPicker.setIs24HourView(true);
        startPicker.setCurrentHour(startHour);
        startPicker.setCurrentMinute(startMinute);
        endPicker.setCurrentHour(endHour);
        endPicker.setCurrentMinute(endMinute);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            startHour = startPicker.getCurrentHour();
            startMinute = startPicker.getCurrentMinute();
            endHour = endPicker.getCurrentHour();
            endMinute = endPicker.getCurrentMinute();
            String value = String.format("%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute);
            android.content.SharedPreferences sharedPreferences = getContext().getSharedPreferences("amio_settings", Context.MODE_PRIVATE);
            sharedPreferences.edit().putString("pref_notification_time_range_notif", value).apply();
            setSummary("From " + String.format("%02d:%02d", startHour, startMinute) + " to " + String.format("%02d:%02d", endHour, endMinute));
            android.util.Log.d("TimeRangePreference", "Time range changed: " + value);
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        android.content.SharedPreferences sharedPreferences = getContext().getSharedPreferences("amio_settings", Context.MODE_PRIVATE);
        String value;
        if (restorePersistedValue) {
            value = sharedPreferences.getString(getKey(), null);
            if (value == null) {
                value = defaultValue != null ? defaultValue.toString() : "18:00-23:00";
            }
        } else {
            value = defaultValue != null ? defaultValue.toString() : "18:00-23:00";
        }
        if (value != null && value.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}")) {
            String[] parts = value.split("[-:]");
            startHour = Integer.parseInt(parts[0]);
            startMinute = Integer.parseInt(parts[1]);
            endHour = Integer.parseInt(parts[2]);
            endMinute = Integer.parseInt(parts[3]);
        }
        setSummary("From " + String.format("%02d:%02d", startHour, startMinute) + " to " + String.format("%02d:%02d", endHour, endMinute));
    }
}
