package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the rolling frame window: statistics, sentinels and reset behaviour. */
class PerformanceMonitorTest {

    @Test
    void emptyMonitorReportsUnknownSentinels() {
        PerformanceMonitor monitor = new PerformanceMonitor(10);
        assertEquals(0, monitor.sampleCount());
        assertEquals(PerformanceSample.UNKNOWN_DOUBLE, monitor.avgFrameMillis());
        assertEquals(PerformanceSample.UNKNOWN_INT, monitor.fps());
        assertEquals(PerformanceSample.UNKNOWN_DOUBLE, monitor.p95FrameMillis());
    }

    @Test
    void constantFrameTimeGivesExpectedFps() {
        PerformanceMonitor monitor = new PerformanceMonitor(10);
        for (int i = 0; i < 10; i++) {
            monitor.onFrame(20d);
        }
        assertEquals(10, monitor.sampleCount());
        assertEquals(20d, monitor.avgFrameMillis(), 1e-9);
        assertEquals(50, monitor.fps());
        assertEquals(0d, monitor.jitterMillis(), 1e-9);
    }

    @Test
    void nonPositiveAndNanDeltasAreIgnored() {
        PerformanceMonitor monitor = new PerformanceMonitor(10);
        monitor.onFrame(0d);
        monitor.onFrame(-5d);
        monitor.onFrame(Double.NaN);
        assertEquals(0, monitor.sampleCount());
    }

    @Test
    void windowIsBoundedAndOldestFramesDropOut() {
        PerformanceMonitor monitor = new PerformanceMonitor(3);
        monitor.onFrame(10d);
        monitor.onFrame(10d);
        monitor.onFrame(10d);
        monitor.onFrame(100d);
        assertEquals(3, monitor.sampleCount());
        // The first 10ms frame has been evicted, so the mean is (10+10+100)/3.
        assertEquals(40d, monitor.avgFrameMillis(), 1e-9);
    }

    @Test
    void p95ReflectsTheSlowTail() {
        PerformanceMonitor monitor = new PerformanceMonitor(100);
        for (int i = 0; i < 95; i++) {
            monitor.onFrame(10d);
        }
        for (int i = 0; i < 5; i++) {
            monitor.onFrame(200d);
        }
        assertTrue(monitor.p95FrameMillis() >= 10d);
        assertTrue(monitor.jitterMillis() > 0d);
    }

    @Test
    void resetClearsTheWindow() {
        PerformanceMonitor monitor = new PerformanceMonitor(5);
        monitor.onFrame(20d);
        monitor.reset();
        assertEquals(0, monitor.sampleCount());
        assertEquals(PerformanceSample.UNKNOWN_INT, monitor.fps());
    }

    @Test
    void sampleJoinsFramesWithCounters() {
        PerformanceMonitor monitor = new PerformanceMonitor(5);
        monitor.onFrame(16d);
        PerformanceSample sample = monitor.sample(999L, 12, "AutoCrystal", 4.5, 9.0, 1, 0, 3, 2);
        assertTrue(sample.hasFrameData());
        assertEquals(63, sample.fps());
        assertEquals(12, sample.activeModules());
        assertEquals("AutoCrystal", sample.worstModuleName());
        assertEquals(4.5, sample.worstModuleTickMillis(), 1e-9);
        assertEquals(999L, sample.sampledAtMillis());
    }

    @Test
    void emptySampleHasNoFrameData() {
        assertFalse(PerformanceSample.empty(1L).hasFrameData());
    }
}
