package com.example.amio;

public interface ServiceBroadcastCallback {
    void onServiceBroadcast(String status, long timestamp, int sensorCount, int lightsOnCount, String sensorDataJson, String fetchErrorsJson);
}
