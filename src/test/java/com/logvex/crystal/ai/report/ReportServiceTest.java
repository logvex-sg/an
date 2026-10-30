package com.logvex.crystal.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.crash.CrashRecord;
import com.logvex.crystal.ai.privacy.PrivacyFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The report pipeline: queueing, approval-gated sending and transport failure handling. */
class ReportServiceTest {

    private CrashRecord record(String fingerprint) {
        return CrashRecord.builder()
                .id(fingerprint + "-1")
                .fingerprint(fingerprint)
                .timestampMillis(1_700_000_000_000L)
                .exceptionType("java.lang.RuntimeException")
                .exceptionMessage("boom")
                .rawStackTrace("at com.logvex.crystal.module.FooModule.tick(FooModule.java:1)")
                .source("thread:Client")
                .build();
    }

    private AISettings settings() {
        AISettings settings = new AISettings();
        settings.emailEnabled = true;
        settings.askBeforeSending = true;
        return settings;
    }

    private ReportService service(AISettings settings, Path file, EmailTransport transport) {
        BugReportQueue queue = new BugReportQueue(file, 16);
        ReportService service = new ReportService(settings, queue,
                new BugReportBuilder(new PrivacyFilter(), settings), message -> { });
        service.setTransport(transport);
        return service;
    }

    @Test
    void queueReportIsDeduplicatedByFingerprint(@TempDir Path dir) {
        ReportService service = service(settings(), dir.resolve("r.json"), (r, s, b) -> { });
        assertNotNull(service.queueReport(record("fp-a"), null));
        assertNull(service.queueReport(record("fp-a"), null), "the same crash is not queued twice");
        assertEquals(1, service.reports().size());
        service.close();
    }

    @Test
    void sendIsGatedByApprovalAndSucceeds(@TempDir Path dir) throws Exception {
        AISettings settings = settings();
        List<String> sent = new CopyOnWriteArrayList<>();
        ReportService service = service(settings, dir.resolve("r.json"),
                (recipient, subject, body) -> sent.add(subject));
        BugReport report = service.queueReport(record("fp-a"), null);
        assertNotNull(report);

        service.approveAndSend(report.id());
        waitFor(() -> !service.reports().isEmpty()
                && service.reports().get(0).status() == BugReport.Status.SENT);

        assertEquals(1, sent.size());
        assertEquals(BugReport.Status.SENT, service.reports().get(0).status());
        service.close();
    }

    @Test
    void sendFailsClosedWhenEmailIsDisabled(@TempDir Path dir) throws Exception {
        AISettings settings = settings();
        settings.emailEnabled = false;
        AtomicInteger attempts = new AtomicInteger();
        ReportService service = service(settings, dir.resolve("r.json"), (r, s, b) -> attempts.incrementAndGet());
        BugReport report = service.queueReport(record("fp-a"), null);

        service.approveAndSend(report.id());
        waitFor(() -> service.reports().get(0).status() == BugReport.Status.FAILED);

        assertEquals(0, attempts.get(), "no transport is touched while sending is disabled");
        assertTrue(service.reports().get(0).lastError().toLowerCase().contains("disabled"));
        service.close();
    }

    @Test
    void transportFailureIsRecordedAndKeptForRetry(@TempDir Path dir) throws Exception {
        ReportService service = service(settings(), dir.resolve("r.json"),
                (recipient, subject, body) -> {
                    throw new IOException("connection refused");
                });
        BugReport report = service.queueReport(record("fp-a"), null);

        service.approveAndSend(report.id());
        waitFor(() -> service.reports().get(0).status() == BugReport.Status.FAILED);

        BugReport failed = service.reports().get(0);
        assertEquals(1, failed.attempts());
        assertTrue(failed.lastError().contains("connection refused"));
        assertFalse(service.reports().isEmpty(), "a failed report stays queued for a retry");
        service.close();
    }

    @Test
    void declinedReportIsNotSent(@TempDir Path dir) {
        AtomicInteger attempts = new AtomicInteger();
        ReportService service = service(settings(), dir.resolve("r.json"), (r, s, b) -> attempts.incrementAndGet());
        BugReport report = service.queueReport(record("fp-a"), null);
        service.decline(report.id());
        assertEquals(BugReport.Status.DECLINED, service.reports().get(0).status());
        assertEquals(0, attempts.get());
        service.close();
    }

    @Test
    void mailtoLinkEncodesRecipientAndBody(@TempDir Path dir) {
        ReportService service = service(settings(), dir.resolve("r.json"), (r, s, b) -> { });
        BugReport report = service.queueReport(record("fp-a"), null);
        String link = service.mailtoLink(report);
        assertTrue(link.startsWith("mailto:" + BugReportBuilder.recipient() + "?subject="));
        assertTrue(link.contains("body="));
        service.close();
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within 5s");
    }
}
