package com.example.communitymember20;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class StepTracker implements SensorEventListener {

    private static final String TAG = "StepTracker";

    // How many km per step (demo scale)
    public static final double KM_PER_STEP = 1.0;

    // Auto-simulation speed on emulator (ms per virtual step)
    private static final long SIM_STEP_INTERVAL_MS = 1500L;

    public interface OnProgress {
        void onStep(int totalSteps, double travelledKm, double totalKm, boolean arrived);
        void onArrived();
    }

    private final SensorManager sensorManager;
    private final Sensor stepDetector;
    private final Sensor stepCounter;
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private final OnProgress listener;
    private final Handler simHandler = new Handler(Looper.getMainLooper());

    private String driverId;
    private String tankerId;
    private String assignmentId;
    private double startLat, startLng;
    private double destLat, destLng;
    private double totalKm;

    private boolean running = false;
    private float baseCounter = -1;
    private int currentSteps = 0;
    private boolean arrivedNotified = false;
    private boolean autoSimulating = false;

    public StepTracker(Context context, OnProgress listener) {
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.stepDetector = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                : null;
        this.stepCounter = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                : null;
    }

    public boolean isSensorAvailable() {
        return stepDetector != null || stepCounter != null;
    }

    public boolean isRunning() { return running; }
    public int getTotalSteps() { return currentSteps; }
    public double getTravelledKm() { return currentSteps * KM_PER_STEP; }
    public double getTotalKm() { return totalKm; }

    public void start(String driverId, String tankerId, String assignmentId,
                      double startLat, double startLng,
                      double destLat, double destLng) {

        stop();

        this.driverId = driverId;
        this.tankerId = tankerId;
        this.assignmentId = assignmentId;
        this.startLat = startLat;
        this.startLng = startLng;
        this.destLat = destLat;
        this.destLng = destLng;

        this.baseCounter = -1;
        this.currentSteps = 0;
        this.arrivedNotified = false;
        this.totalKm = haversine(startLat, startLng, destLat, destLng);

        if (totalKm < 0.1) totalKm = 1.0;

        running = true;
        Log.d(TAG, "Trip started. Total: " + totalKm + " km");

        boolean hasRealSensor = false;

        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Registered STEP_DETECTOR");
            hasRealSensor = true;
        }
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Registered STEP_COUNTER");
            hasRealSensor = true;
        }

        // ✅ No physical sensor → auto-simulate walking on a timer
        if (!hasRealSensor) {
            Log.d(TAG, "No sensor — starting auto-simulation");
            autoSimulating = true;
            simHandler.postDelayed(simRunnable, SIM_STEP_INTERVAL_MS);
        }

        pushPosition(startLat, startLng, 0, totalKm);
    }

    public void stop() {
        running = false;
        autoSimulating = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        simHandler.removeCallbacksAndMessages(null);
    }

    private final Runnable simRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || !autoSimulating) return;
            currentSteps += 1;
            Log.d(TAG, "Auto-step → " + currentSteps);
            handleProgress();
            if (running) {
                simHandler.postDelayed(this, SIM_STEP_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!running) return;

        int type = event.sensor.getType();

        if (type == Sensor.TYPE_STEP_DETECTOR) {
            currentSteps += 1;
            Log.d(TAG, "STEP_DETECTOR → steps=" + currentSteps);
            handleProgress();
        } else if (type == Sensor.TYPE_STEP_COUNTER) {
            float cumulative = event.values[0];
            if (baseCounter < 0) {
                baseCounter = cumulative;
                return;
            }
            int hardwareSteps = (int) (cumulative - baseCounter);
            if (hardwareSteps > currentSteps) {
                currentSteps = hardwareSteps;
                Log.d(TAG, "STEP_COUNTER → steps=" + currentSteps);
                handleProgress();
            }
        }
    }

    private void handleProgress() {
        double travelledKm = currentSteps * KM_PER_STEP;

        if (travelledKm >= totalKm) {
            travelledKm = totalKm;
            pushPosition(destLat, destLng, travelledKm, totalKm);

            if (!arrivedNotified) {
                arrivedNotified = true;
                if (listener != null) listener.onArrived();
            }
            if (listener != null) {
                listener.onStep(currentSteps, travelledKm, totalKm, true);
            }
            stop();
            return;
        }

        double t = travelledKm / totalKm;
        double lat = startLat + (destLat - startLat) * t;
        double lng = startLng + (destLng - startLng) * t;

        pushPosition(lat, lng, travelledKm, totalKm);

        if (listener != null) {
            listener.onStep(currentSteps, travelledKm, totalKm, false);
        }
    }

    private void pushPosition(double lat, double lng, double travelledKm, double totalKm) {
        if (driverId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("driverId", driverId);
        data.put("tankerId", tankerId);
        data.put("latitude", lat);
        data.put("longitude", lng);
        data.put("speed", 1.4f);
        data.put("lastUpdated", System.currentTimeMillis());
        data.put("travelledKm", travelledKm);
        data.put("totalKm", totalKm);
        data.put("steps", currentSteps);
        if (assignmentId != null) data.put("assignmentId", assignmentId);

        dbRef.child("tanker_locations").child(driverId).setValue(data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }
}