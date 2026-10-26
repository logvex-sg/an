package com.logvex.crystal.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Tests the logger's orchestration: threshold filtering, duplicate suppression across
 * scans, dimension handling, and the local report contents. The worker is a synchronous
 * executor so a scan completes before the test polls, keeping the test deterministic.
 */
class ChestLoggerServiceTest {

    /** Runs every task inline on the calling thread. */
    private static final class InlineExecutor extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private static ChestLoggerService service() {
        ExecutorService inline = new InlineExecutor();
        ChestLoggerService service = new ChestLoggerService(inline, false);
        service.setRunning(true);
        return service;
    }

    private static List<ChestPoint> haul(int count, int startX) {
        List<ChestPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new ChestPoint(startX + i, 64, 0));
        }
        return points;
    }

    @Test
    void clusterAtOrAboveThresholdIsReported() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of("Alex"), 1_000L);
        List<ChestReport> reports = service.poll();
        assertEquals(1, reports.size());
        ChestReport report = reports.get(0);
        assertEquals(30, report.chestCount());
        assertEquals("minecraft:overworld", report.dimension());
        assertEquals(List.of("Alex"), report.nearbyPlayers());
        assertEquals(1_000L, report.discoveredAtMillis());
    }

    @Test
    void clusterBelowThresholdIsNotReported() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(29, 0), List.of(), 1_000L);
        assertTrue(service.poll().isEmpty(), "29 chests is below the 30 threshold");
    }

    @Test
    void repeatedScanOfTheSameHaulIsSuppressed() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertEquals(1, service.poll().size());
        // Scan again a moment later: the same cluster must not be reported twice.
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 2_000L);
        assertTrue(service.poll().isEmpty(), "duplicate report was not suppressed");
    }

    @Test
    void reportReappearsAfterCooldown() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertEquals(1, service.poll().size());
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L + 300_001L);
        assertEquals(1, service.poll().size(), "after the cooldown the find is reportable again");
    }

    @Test
    void aNewDistantHaulIsReportedImmediately() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertEquals(1, service.poll().size());
        service.submit("minecraft:overworld", haul(30, 5000), List.of(), 1_100L);
        assertEquals(1, service.poll().size(), "a different haul is a new find");
    }

    @Test
    void dimensionChangeResetsSuppression() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertEquals(1, service.poll().size());
        service.onDimensionChange("minecraft:the_nether");
        service.submit("minecraft:the_nether", haul(30, 0), List.of(), 1_100L);
        assertEquals(1, service.poll().size(), "the same coordinates in another dimension are a new find");
    }

    @Test
    void malformedSnapshotDoesNotThrowOrReport() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        // No points at all: nothing to report, and no exception.
        service.submit("minecraft:overworld", List.of(), List.of(), 1_000L);
        assertTrue(service.poll().isEmpty());
    }

    @Test
    void disabledServiceIgnoresSubmissions() {
        ChestLoggerService service = new ChestLoggerService(new InlineExecutor(), false);
        service.setRunning(false);
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertTrue(service.poll().isEmpty(), "a stopped logger must not scan");
    }

    @Test
    void pollDrainsSoReportsAreDeliveredOnce() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        assertEquals(1, service.poll().size());
        assertTrue(service.poll().isEmpty(), "poll must drain the queue");
    }

    @Test
    void reportSummaryMentionsCountCoordinatesAndPlayers() {
        ChestReport report = new ChestReport("id", "minecraft:overworld", 10, 64, -20, 42,
                1_000L, List.of("Alex", "Sam"));
        String summary = report.summary();
        assertTrue(summary.contains("42 chests"), summary);
        assertTrue(summary.contains("10 64 -20"), summary);
        assertTrue(summary.contains("overworld"), summary);
        assertTrue(summary.contains("Alex, Sam"), summary);
    }

    @Test
    void resetClearsSuppressionAndPending() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_000L);
        service.reset();
        assertTrue(service.poll().isEmpty(), "reset must drop pending reports");
        service.submit("minecraft:overworld", haul(30, 0), List.of(), 1_100L);
        assertEquals(1, service.poll().size(), "after a reset the same haul is reportable again");
    }

    @Test
    void pruneDropsSuppressionRecordsOlderThanTheWindow() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(2, 2, 1, 30, 16, 300_000L));
        // Report many distinct bases far apart so each is remembered separately.
        for (int i = 0; i < 20; i++) {
            service.submit("minecraft:overworld", haul(30, i * 500), List.of(), 1_000L);
        }
        assertEquals(20, service.suppressionCount());
        // A prune far beyond the cooldown must forget them all.
        service.prune(1_000L + 10_000_000L, 300_000L);
        assertEquals(0, service.suppressionCount());
    }

    @Test
    void configureIsReflectedInConfigAccessor() {
        ChestLoggerService service = service();
        service.configure(new ChestLoggerService.Config(3, 4, 2, 25, 8, 60_000L));
        assertEquals(25, service.config().reportThreshold());
        assertEquals(4, service.config().linkDistance());
        assertEquals(60_000L, service.config().cooldownMillis());
        assertTrue(service.isRunning());
    }
}
