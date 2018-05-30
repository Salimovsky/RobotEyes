package com.addi.salim.robot_eyes;

import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class RangeFinderManager {

    private final int DISTANCE_THRESHOLD_ALARM = 50; // 50cm
    private final Map<Integer, ObjectRangeListener> listeners = new WeakHashMap<>();
    private final Map<Integer, SignalFilter> facesDistanceFilters = new HashMap<>();
    private final ArduinoManager arduinoManager;

    // Because the Ultrasonic range finder latency is at least 60ms,
    // then we should not send data at a frequency more than what the ultrasonic could process
    // thus the android app transmission latency should be at least 60ms!
    // we will do 15Hz which is the camera frames frequency
    private static long TRANSMISSION_LATENCY = TimeUnit.MILLISECONDS.toMillis(65);
    private long lastSentTime = 0;
    private Set<Integer> objectsWithinAlarmDistance = new HashSet<>();
    private boolean isAlarmOn = false;

    public RangeFinderManager(ArduinoManager arduinoManager) {
        this.arduinoManager = arduinoManager;
    }

    public synchronized void estimateDistanceRange(int objectId, double angleToObject) {
        Log.e("****", " id = " + objectId + " angle = " + angleToObject);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSentTime > TRANSMISSION_LATENCY) {
            lastSentTime = currentTime;
            arduinoManager.sendObjectAngle(objectId, (float) angleToObject);
        }
    }

    public synchronized void onNewFaceAdded(int id, ObjectRangeListener listener) {
        listeners.put(id, listener);
        facesDistanceFilters.put(id, new SignalFilter());
    }

    public synchronized void onFaceRemoved(int id) {
        listeners.remove(id);
        objectsWithinAlarmDistance.remove(id);
        facesDistanceFilters.remove(id);

        if (isAlarmOn && objectsWithinAlarmDistance.isEmpty()) {
            isAlarmOn = false;
            arduinoManager.sendDismissAlarm();
        }

        arduinoManager.sendObjectRemoved(id);
    }

    public synchronized void notify(int id, int distanceInCentimeter) {
        final SignalFilter signalFilter = facesDistanceFilters.get(id);
        final ObjectRangeListener listener = listeners.get(id);
        if (listener != null) {
            // filter out noise
            signalFilter.add(distanceInCentimeter);
            final float smoothedDistance = signalFilter.get();
            listener.onDistanceUpdated(id, (int) smoothedDistance);
            if (smoothedDistance <= DISTANCE_THRESHOLD_ALARM) {
                objectsWithinAlarmDistance.add(id);
                if (!isAlarmOn) {
                    isAlarmOn = true;
                    arduinoManager.sendObjectDistanceAlarm(id);
                }
            } else {
                objectsWithinAlarmDistance.remove(id);
                if (isAlarmOn && objectsWithinAlarmDistance.isEmpty()) {
                    isAlarmOn = false;
                    arduinoManager.sendDismissAlarm();
                }
            }
        }
    }

    private static class SignalFilter {
        private static final int WINDOW_SIZE = 5;
        private final float[] filterWindow = new float[WINDOW_SIZE];
        private int windowIndex = 0;
        private boolean initialized;

        public synchronized void add(float data) {
            if (initialized) {
                filterWindow[windowIndex] = data;
            } else {
                initialized = true;
                for (int i = 0; i < WINDOW_SIZE; i++) {
                    filterWindow[i] = data;
                }
            }

            windowIndex++;
            if (windowIndex % WINDOW_SIZE == 0) {
                windowIndex = 0;
            }
        }

        public synchronized float get() {
            float sum = 0;
            for (int i = 0; i < WINDOW_SIZE; i++) {
                sum += filterWindow[i];
            }

            return sum / (float) WINDOW_SIZE;
        }
    }
}
