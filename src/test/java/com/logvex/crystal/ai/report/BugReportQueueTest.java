package com.logvex.crystal.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The persisted report queue: dedup, capacity, status transitions and resilience. */
class BugReportQueueTest {

    private BugReport report(String id, String fingerprint) {
        return new BugReport(id, fingerprint, 1_000L, "subject", "body",
                BugReport.Status.PENDING, 0, "");
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("reports.json");
        BugReportQueue queue = new BugReportQueue(file, 8);
        assertTrue(queue.add(report("a", "fp-a")));
        assertTrue(queue.add(report("b", "fp-b")));
        queue.save();

        BugReportQueue reloaded = new BugReportQueue(file, 8);
        reloaded.load();
        assertEquals(2, reloaded.size());
        assertNotNull(reloaded.byId("a"));
        assertEquals("fp-b", reloaded.byId("b").fingerprint());
    }

    @Test
    void duplicateFingerprintIsRejected() {
        BugReportQueue queue = new BugReportQueue(null, 8);
        assertTrue(queue.add(report("a", "fp")));
        assertFalse(queue.add(report("b", "fp")), "the same crash must not be queued twice");
        assertEquals(1, queue.size());
    }

    @Test
    void blankFingerprintIsRejected() {
        BugReportQueue queue = new BugReportQueue(null, 8);
        assertFalse(queue.add(report("a", "")));
        assertFalse(queue.add(null));
    }

    @Test
    void capacityDropsOldestFirst() {
        BugReportQueue queue = new BugReportQueue(null, 2);
        queue.add(report("a", "fp-a"));
        queue.add(report("b", "fp-b"));
        queue.add(report("c", "fp-c"));
        assertEquals(2, queue.size());
        assertNull(queue.byId("a"), "the oldest report is evicted");
        assertNotNull(queue.byId("c"));
    }

    @Test
    void updateReplacesStatus() {
        BugReportQueue queue = new BugReportQueue(null, 8);
        queue.add(report("a", "fp-a"));
        BugReport sent = queue.byId("a").withStatus(BugReport.Status.SENT, 1, "");
        assertTrue(queue.update(sent));
        assertEquals(BugReport.Status.SENT, queue.byId("a").status());
    }

    @Test
    void pendingIncludesFailedButNotSentOrDeclined() {
        BugReportQueue queue = new BugReportQueue(null, 8);
        queue.add(report("a", "fp-a"));
        queue.add(report("b", "fp-b").withStatus(BugReport.Status.SENT, 1, ""));
        queue.add(report("c", "fp-c").withStatus(BugReport.Status.FAILED, 1, "err"));
        queue.add(report("d", "fp-d").withStatus(BugReport.Status.DECLINED, 0, ""));
        assertEquals(2, queue.pending().size());
    }

    @Test
    void containsFingerprintIgnoresDeclined() {
        BugReportQueue queue = new BugReportQueue(null, 8);
        queue.add(report("a", "fp-a").withStatus(BugReport.Status.DECLINED, 0, ""));
        assertFalse(queue.containsFingerprint("fp-a"));
        queue.add(report("b", "fp-b"));
        assertTrue(queue.containsFingerprint("fp-b"));
    }

    @Test
    void newerSchemaIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("reports.json");
        Files.writeString(file, "{\"schema\":" + (BugReportQueue.SCHEMA_VERSION + 1)
                + ",\"reports\":[]}");
        BugReportQueue queue = new BugReportQueue(file, 8);
        queue.add(report("a", "fp-a"));
        queue.load();
        assertEquals(0, queue.size(), "a future schema must be ignored");
    }

    @Test
    void corruptFileLoadsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("reports.json");
        Files.writeString(file, "{ not json");
        BugReportQueue queue = new BugReportQueue(file, 8);
        queue.add(report("a", "fp-a"));
        queue.load();
        assertEquals(0, queue.size());
    }
}
