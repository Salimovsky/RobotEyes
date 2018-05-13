package com.addi.salim.night_light;

import java.util.concurrent.TimeUnit;

public class LowPassFilter {
    // time constant within which signal will be filtered out
    private final double rc;
    private double[] lastSignal;
    private long timestampNanoSecondsZero;
    private long count;
    private boolean isInitialized;

    public LowPassFilter(double timeConstantRC) {
        this.rc = timeConstantRC;
    }

    public synchronized void reset() {
        this.isInitialized = false;
        this.lastSignal = null;
        this.count = 0;
        this.timestampNanoSecondsZero = 0;
    }

    public synchronized void initialize(long timestampNanoSecondsZero) {
        reset();
        this.isInitialized = true;
        this.timestampNanoSecondsZero = timestampNanoSecondsZero;
    }

    public synchronized boolean isInitialized() {
        return isInitialized;
    }

    public synchronized double[] applyLowPassFilter(long timestampNanoSeconds, double... input) {
        // calculate dynamically delivery time dt:
        count++;
        final double dt = (double) (timestampNanoSeconds - timestampNanoSecondsZero) / (double) (count * TimeUnit.SECONDS.toNanos(1));
        final double alpha = dt / (rc + dt);

        if (lastSignal == null) {
            lastSignal = new double[input.length];
        }

        final double[] filteredSignal = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            filteredSignal[i] = (input[i] * alpha) + ((1 - alpha) * lastSignal[i]);
            lastSignal[i] = filteredSignal[i];
        }
        return filteredSignal;
    }
}
