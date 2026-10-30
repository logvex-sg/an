package com.logvex.crystal.commandcenter;

import java.util.Arrays;

/**
 * A bounded rolling window of frame times that turns the render loop into the numbers the
 * performance intelligence judges.
 *
 * <p>It is fed one delta per rendered frame from the frame clock and keeps the last
 * {@code window} of them; average, 95th percentile, jitter and FPS are computed from that
 * window on demand. Everything is a plain double, so the monitor is Minecraft-independent
 * and directly unit testable. The window is small and fixed, so memory use is constant and
 * the cost per frame is one array write.
 *
 * <p>The monitor only measures; it never changes a setting. {@link #sample} is where the
 * measured frames are joined with the other subsystem counters to produce the
 * {@link PerformanceSample} the rest of the Command Center consumes.
 */
public final class PerformanceMonitor {

    /** Frames retained for the statistics; about two seconds at 60 fps. */
    public static final int DEFAULT_WINDOW = 120;

    private final double[] frames;
    private int next;
    private int filled;

    public PerformanceMonitor() {
        this(DEFAULT_WINDOW);
    }

    public PerformanceMonitor(int window) {
        this.frames = new double[Math.max(1, window)];
    }

    /** Records one frame's duration in milliseconds. Non-positive deltas are ignored. */
    public void onFrame(double deltaMillis) {
        if (!(deltaMillis > 0) || Double.isNaN(deltaMillis)) {
            return;
        }
        frames[next] = deltaMillis;
        next = (next + 1) % frames.length;
        if (filled < frames.length) {
            filled++;
        }
    }

    /** How many frames are currently in the window. */
    public int sampleCount() {
        return filled;
    }

    public void reset() {
        Arrays.fill(frames, 0d);
        next = 0;
        filled = 0;
    }

    /** Mean frame time over the window, or {@link PerformanceSample#UNKNOWN_DOUBLE}. */
    public double avgFrameMillis() {
        if (filled == 0) {
            return PerformanceSample.UNKNOWN_DOUBLE;
        }
        double sum = 0;
        for (int i = 0; i < filled; i++) {
            sum += frames[i];
        }
        return sum / filled;
    }

    /** Frames per second derived from the mean frame time, or -1 when unmeasured. */
    public int fps() {
        double avg = avgFrameMillis();
        if (avg <= 0) {
            return PerformanceSample.UNKNOWN_INT;
        }
        return (int) Math.round(1000d / avg);
    }

    /** The 95th-percentile frame time, or {@link PerformanceSample#UNKNOWN_DOUBLE}. */
    public double p95FrameMillis() {
        double[] sorted = sorted();
        if (sorted.length == 0) {
            return PerformanceSample.UNKNOWN_DOUBLE;
        }
        int index = (int) Math.ceil(sorted.length * 0.95) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    /** The frame-time standard deviation, a proxy for stutter. */
    public double jitterMillis() {
        if (filled < 2) {
            return 0d;
        }
        double mean = avgFrameMillis();
        double sum = 0;
        for (int i = 0; i < filled; i++) {
            double d = frames[i] - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / filled);
    }

    private double[] sorted() {
        double[] copy = Arrays.copyOf(frames, filled);
        Arrays.sort(copy);
        return copy;
    }

    /**
     * Builds a {@link PerformanceSample} from the measured frames plus the current
     * subsystem counters. The counters are passed in rather than read here so the monitor
     * stays independent of every other service.
     */
    public PerformanceSample sample(long nowMillis, int activeModules, String worstModuleName,
                                    double worstModuleTickMillis, double totalModuleTickMillis,
                                    int aiQueueDepth, int aiRejected, int eventBacklog,
                                    int activeActions) {
        return new PerformanceSample(fps(), avgFrameMillis(), p95FrameMillis(), jitterMillis(),
                worstModuleName == null ? "" : worstModuleName,
                worstModuleTickMillis, totalModuleTickMillis, activeModules, aiQueueDepth,
                aiRejected, eventBacklog, activeActions, nowMillis);
    }
}
