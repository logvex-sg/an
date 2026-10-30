package com.logvex.crystal.ai.report;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.crash.CrashRecord;
import com.logvex.crystal.ai.crash.Diagnosis;
import com.logvex.crystal.ai.model.InferenceExecutor;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * The report pipeline: build a sanitized report from a crash, queue it, and send it only with
 * the user's approval (or automatically when they have explicitly turned that on).
 *
 * <p>All disk and network work runs on a dedicated single-thread executor, never the game
 * thread. The queue is persisted after every change and reloaded at startup, so a report made
 * just before a crash survives into the next launch. Sending always goes through the configured
 * {@link EmailTransport}; when none is usable the caller can fall back to a {@code mailto:} link.
 */
public final class ReportService implements AutoCloseable {

    /** Notified with a short message for the client's notification manager. */
    private final Consumer<String> notifier;
    private final AISettings settings;
    private final BugReportQueue queue;
    private final BugReportBuilder builder;
    private final InferenceExecutor executor;
    private volatile EmailTransport transportOverride;

    public ReportService(AISettings settings, BugReportQueue queue, BugReportBuilder builder,
                         Consumer<String> notifier) {
        this.settings = settings;
        this.queue = queue;
        this.builder = builder;
        this.notifier = notifier != null ? notifier : message -> { };
        this.executor = new InferenceExecutor(1, 2, "logvex-report");
        queue.load();
    }

    /** Injects a transport (used by tests and by the mailto-only configuration). */
    public void setTransport(EmailTransport transport) {
        this.transportOverride = transport;
    }

    public BugReportQueue queue() {
        return queue;
    }

    public List<BugReport> reports() {
        return queue.all();
    }

    /**
     * Called when the crash monitor records a new crash. Builds and queues a report off-thread
     * when the queue is enabled, then auto-sends only if the user opted out of per-report
     * approval and enabled email.
     */
    public void onCrash(CrashRecord record, Diagnosis diagnosis) {
        if (record == null || !settings.reportQueue) {
            return;
        }
        executor.submit(() -> {
            try {
                BugReport report = builder.build(record, diagnosis);
                if (queue.add(report)) {
                    persist();
                    notifier.accept("Crash captured — report ready for review (\u2661)");
                    if (settings.emailEnabled && !settings.askBeforeSending) {
                        sendInternal(report.id());
                    }
                }
            } catch (RuntimeException e) {
                // Building a report must never break crash handling.
            }
            return null;
        });
    }

    /** Builds and queues a report synchronously; returns it, or null when it was a duplicate. */
    public BugReport queueReport(CrashRecord record, Diagnosis diagnosis) {
        BugReport report = builder.build(record, diagnosis);
        if (queue.add(report)) {
            persist();
            return report;
        }
        return null;
    }

    /** Sends a queued report now, after explicit user approval. */
    public void approveAndSend(String reportId) {
        executor.submit(() -> {
            sendInternal(reportId);
            return null;
        });
    }

    /** Marks a report declined so it is not sent and not re-queued. */
    public void decline(String reportId) {
        BugReport report = queue.byId(reportId);
        if (report != null) {
            queue.update(report.withStatus(BugReport.Status.DECLINED, report.attempts(), "declined by user"));
            persist();
        }
    }

    /** Retries a failed report. */
    public void retry(String reportId) {
        approveAndSend(reportId);
    }

    public boolean delete(String reportId) {
        boolean removed = queue.remove(reportId);
        if (removed) {
            persist();
        }
        return removed;
    }

    public void clear() {
        queue.clear();
        persist();
    }

    /** A {@code mailto:} link for the report, for clients that would rather send it themselves. */
    public String mailtoLink(BugReport report) {
        return mailtoLink(report, true);
    }

    /** Builds a mailto link; {@code includeBody} false sends the subject only. */
    public String mailtoLink(BugReport report, boolean includeBody) {
        String subject = encode(report.subject());
        StringBuilder link = new StringBuilder("mailto:")
                .append(BugReportBuilder.recipient())
                .append("?subject=").append(subject);
        if (includeBody) {
            link.append("&body=").append(encode(report.body()));
        }
        return link.toString();
    }

    /** A short preview of a report body for the UI. */
    public String preview(BugReport report, int maxChars) {
        String body = report.body();
        if (body.length() <= maxChars) {
            return body;
        }
        return body.substring(0, Math.max(0, maxChars)) + "\n… (" + report.sizeBytes() + " bytes total)";
    }

    private void sendInternal(String reportId) {
        BugReport report = queue.byId(reportId);
        if (report == null) {
            return;
        }
        if (!settings.emailEnabled) {
            queue.update(report.withStatus(BugReport.Status.FAILED, report.attempts(),
                    "Email sending is disabled in settings"));
            persist();
            notifier.accept("Email sending is disabled — use the mailto link instead");
            return;
        }
        EmailTransport transport = transportOverride;
        if (transport == null) {
            transport = new SmtpEmailTransport(settings);
        }
        try {
            transport.send(BugReportBuilder.recipient(), report.subject(), report.body());
            queue.update(report.withStatus(BugReport.Status.SENT, report.attempts() + 1, ""));
            notifier.accept("Report sent to " + BugReportBuilder.recipient());
        } catch (IOException | RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            queue.update(report.withStatus(BugReport.Status.FAILED, report.attempts() + 1, message));
            notifier.accept("Report could not be sent: " + message);
        }
        persist();
    }

    private void persist() {
        try {
            queue.save();
        } catch (IOException e) {
            // Persistence failure is reported through the notification channel only.
            notifier.accept("Could not save the report queue: " + e.getMessage());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        executor.close();
    }
}
