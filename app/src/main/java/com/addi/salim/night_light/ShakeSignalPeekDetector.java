package com.addi.salim.night_light;

import java.util.concurrent.TimeUnit;

public class ShakeSignalPeekDetector {
    public final static double RC = 1d / 3d; // constant time in seconds equivalent to 3Hz.
    private boolean isInitialized;
    // Assuming that the fastest a user can walk is f = 1/RC = 3 steps per seconds!
    private final static int MIN_PERIOD_TIME = (int) (RC * TimeUnit.SECONDS.toMillis(1));
    private final long samplingMinPeriod = TimeUnit.MILLISECONDS.toMillis(10);
    private final double[] signalPoints = new double[30];
    private long lastSignalPointTimestamp;
    private long lastUpPeekTimestamp;
    private final static double VARIATION_MIN_THRESHOLD = 0.8d;
    private final static double VARIATION_MAX_THRESHOLD = 2.5d;


    public synchronized void reset() {
        isInitialized = false;
        for (int i = 0; i < signalPoints.length; i++) {
            signalPoints[i] = 0;
        }
        lastSignalPointTimestamp = 0;
        lastUpPeekTimestamp = 0;
    }

    public synchronized void initialize(long timestampNanoSecondsZero) {
        reset();
        isInitialized = true;
        lastUpPeekTimestamp = timestampNanoSecondsZero;
    }

    public synchronized boolean isInitialized() {
        return isInitialized;
    }

    public synchronized boolean addAndDetectPeak(long timestampNanoSeconds, double x) {
        if (TimeUnit.NANOSECONDS.toMillis(timestampNanoSeconds - lastSignalPointTimestamp) >= samplingMinPeriod) {
            for (int i = 1; i < signalPoints.length; i++) {
                signalPoints[i - 1] = signalPoints[i];
            }

            signalPoints[signalPoints.length - 1] = x;
            lastSignalPointTimestamp = timestampNanoSeconds;
            final boolean isUpPeek = isPositivePeek();
            if (isUpPeek) {
                final long period = TimeUnit.NANOSECONDS.toMillis(timestampNanoSeconds - lastUpPeekTimestamp);
                if (period > MIN_PERIOD_TIME) {
                    lastUpPeekTimestamp = timestampNanoSeconds;
                    final double variation = getSampleSignalVariationAmplitude();
                    if (variation > VARIATION_MIN_THRESHOLD && variation < VARIATION_MAX_THRESHOLD) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isPositivePeek() {
        final int windowLength = signalPoints.length;
        final int midWindow = windowLength / 2;

        for (int i = 0; i < windowLength; i++) {
            if (!(signalPoints[midWindow] >= signalPoints[i])) {
                return false;
            }
        }

        return true;
    }

    private double getSampleSignalVariationAmplitude() {
        double max = -1 * Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        final int windowLength = signalPoints.length;
        for (int i = 0; i < windowLength; i++) {
            if (max < signalPoints[i]) {
                max = signalPoints[i];
            }
            if (min > signalPoints[i]) {
                min = signalPoints[i];
            }
        }

        return max - min;
    }
}
