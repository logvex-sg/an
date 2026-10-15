package com.logvex.crystal.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests duplicate suppression: proximity matching and the cooldown, independently and
 * together.
 */
class ClusterReporterTest {

    private static ChestCluster cluster(int x, int y, int z, int count) {
        return new ChestCluster(x, y, z, count, x, y, z, x, y, z);
    }

    @Test
    void firstFindIsReported() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        assertEquals(1, reporter.size());
    }

    @Test
    void sameClusterWithinCooldownIsSuppressed() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        // A slightly different centre (edge chunks loaded) is still the same find.
        assertFalse(reporter.shouldReport(cluster(3, 64, 2, 41), 2_000L));
    }

    @Test
    void sameClusterAfterCooldownIsReportedAgain() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L + 300_001L));
    }

    @Test
    void distantClusterIsReportedImmediately() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        // Far outside the proximity radius: a genuinely different find.
        assertTrue(reporter.shouldReport(cluster(1000, 64, 1000, 40), 1_100L));
        assertEquals(2, reporter.size());
    }

    @Test
    void verticalSeparationBeyondRadiusIsNotSuppressed() {
        ClusterReporter reporter = new ClusterReporter(8, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 10, 0, 40), 1_000L));
        // 40 blocks higher is outside the radius, so it is a new find.
        assertTrue(reporter.shouldReport(cluster(0, 50, 0, 40), 1_100L));
    }

    @Test
    void clearForgetsEverything() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        reporter.clear();
        assertEquals(0, reporter.size());
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_100L),
                "after a clear (e.g. dimension change) the find is reportable again");
    }

    @Test
    void pruneDropsStaleRecords() {
        ClusterReporter reporter = new ClusterReporter(16, 300_000L);
        assertTrue(reporter.shouldReport(cluster(0, 64, 0, 40), 1_000L));
        reporter.prune(1_000L + 600_000L, 300_000L);
        assertEquals(0, reporter.size());
    }
}
