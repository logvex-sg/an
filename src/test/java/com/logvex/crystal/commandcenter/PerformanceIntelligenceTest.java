package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the deterministic performance rules and their severity mapping. */
class PerformanceIntelligenceTest {

    private final PerformanceIntelligence intelligence = new PerformanceIntelligence();

    private static PerformanceSample sample(int fps, double p95, double jitter, String worstModule,
                                            double worstTick, int aiQueue, int aiRejected,
                                            int backlog, int activeActions) {
        return new PerformanceSample(fps, fps > 0 ? 1000d / fps : -1d, p95, jitter, worstModule,
                worstTick, worstTick, 5, aiQueue, aiRejected, backlog, activeActions, 0L);
    }

    @Test
    void healthySampleProducesNoFindings() {
        PerformanceReport report = intelligence.analyse(sample(144, 8, 2, "", 0.5, 0, 0, 0, 0));
        assertTrue(report.clean());
        assertEquals(HealthStatus.OK, report.status());
    }

    @Test
    void lowFrameRateIsCritical() {
        PerformanceReport report = intelligence.analyse(sample(20, 60, 5, "", 0.1, 0, 0, 0, 0));
        assertFalse(report.clean());
        assertEquals(HealthStatus.CRITICAL, report.status());
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("low-fps-critical")));
    }

    @Test
    void moderateFrameRateIsAttention() {
        PerformanceReport report = intelligence.analyse(sample(45, 25, 5, "", 0.1, 0, 0, 0, 0));
        assertEquals(HealthStatus.ATTENTION, report.status());
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("low-fps")));
    }

    @Test
    void heavyModuleTickIsFlagged() {
        PerformanceReport report = intelligence.analyse(sample(144, 8, 2, "AutoCrystal", 6.0, 0, 0, 0, 0));
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("module-tick")));
    }

    @Test
    void saturatedAiQueueIsFlagged() {
        PerformanceReport report = intelligence.analyse(sample(144, 8, 2, "", 0.1, 3, 0, 0, 0));
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("ai-queue")));
    }

    @Test
    void actionContentionIsFlagged() {
        PerformanceReport report = intelligence.analyse(sample(144, 8, 2, "", 0.1, 0, 0, 0, 3));
        assertTrue(report.findings().stream().anyMatch(f -> f.rule().equals("action-contention")));
    }

    @Test
    void findingsAreOrderedMostSevereFirst() {
        PerformanceReport report = intelligence.analyse(sample(20, 60, 20, "Heavy", 6.0, 3, 2, 5, 2));
        assertEquals(HealthStatus.CRITICAL, report.findings().get(0).severity());
        for (int i = 1; i < report.findings().size(); i++) {
            assertTrue(report.findings().get(i - 1).severity().ordinal()
                    >= report.findings().get(i).severity().ordinal());
        }
    }

    @Test
    void nullSampleIsHandled() {
        PerformanceReport report = intelligence.analyse(null);
        assertFalse(report.sample().hasFrameData());
    }

    @Test
    void findingDescribeCombinesObservationAndSuggestion() {
        PerformanceFinding finding = new PerformanceFinding("r", HealthStatus.INFO, "saw x", "try y", "x");
        assertEquals("saw x \u2014 try y", finding.describe());
        assertEquals("saw x", new PerformanceFinding("r", HealthStatus.INFO, "saw x", "", "x").describe());
    }
}
