package com.logvex.crystal.ai.crash;

import com.logvex.crystal.ai.context.DiagnosticContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Captures uncaught exceptions cheaply and files them with the {@link CrashMonitor}.
 *
 * <p>It installs a {@link Thread#setDefaultUncaughtExceptionHandler default uncaught-exception
 * handler}, which only sees exceptions that no thread-local handler (including Minecraft's own
 * crash reporting) already handled. It <em>always</em> delegates to the previously-installed
 * handler, so Minecraft's normal crash report and Fabric's error handling are untouched.
 *
 * <p>The handler path is intentionally tiny: format the trace, classify, fingerprint, record.
 * No disk write, no network, no model. Persistence is a listener on the monitor that runs later.
 */
public final class CrashCapture {

    private final CrashMonitor monitor;
    private final DiagnosticContext context;
    private final BooleanSupplier enabled;
    private final Supplier<String> baritoneStatus;
    private final Supplier<String> aiStatus;
    private volatile Thread.UncaughtExceptionHandler previous;

    public CrashCapture(CrashMonitor monitor, DiagnosticContext context, BooleanSupplier enabled,
                        Supplier<String> baritoneStatus, Supplier<String> aiStatus) {
        this.monitor = monitor;
        this.context = context;
        this.enabled = enabled;
        this.baritoneStatus = baritoneStatus;
        this.aiStatus = aiStatus;
    }

    /** Installs the default handler, chaining to whatever was there before. Idempotent. */
    public synchronized void install() {
        if (previous != null) {
            return;
        }
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this::handle);
    }

    /** Restores the previous handler. Idempotent. */
    public synchronized void uninstall() {
        Thread.UncaughtExceptionHandler prior = previous;
        previous = null;
        if (prior != null) {
            Thread.setDefaultUncaughtExceptionHandler(prior);
        }
    }

    private void handle(Thread thread, Throwable error) {
        try {
            capture("thread:" + (thread == null ? "?" : thread.getName()), error);
        } catch (Throwable ignored) {
            // Capturing must never make a crash worse.
        }
        Thread.UncaughtExceptionHandler prior = previous;
        if (prior != null) {
            prior.uncaughtException(thread, error);
        }
    }

    /**
     * Builds and records a crash record for {@code error}. Also used directly for module and
     * integration exceptions caught elsewhere. Returns the record, or null when disabled.
     */
    public CrashRecord capture(String source, Throwable error) {
        if (error == null || !enabled.getAsBoolean()) {
            return null;
        }
        CrashClassifier.Result result = CrashClassifier.classify(error);
        String fingerprint = CrashFingerprint.of(error);
        DiagnosticContext.Snapshot snap = context.snapshot();
        CrashRecord record = CrashRecord.builder()
                .id(fingerprint + "-" + System.currentTimeMillis())
                .fingerprint(fingerprint)
                .timestampMillis(System.currentTimeMillis())
                .exceptionType(error.getClass().getName())
                .exceptionMessage(error.getMessage() == null ? "" : error.getMessage())
                .rawStackTrace(stackTraceOf(error))
                .category(result.category())
                .severity(result.severity())
                .suspectedModule(result.suspectedModule())
                .source(source == null ? "unknown" : source)
                .modVersion(snap.modVersion())
                .minecraftVersion(snap.minecraftVersion())
                .loaderVersion(snap.loaderVersion())
                .javaVersion(snap.javaVersion())
                .osName(snap.osName())
                .baritoneStatus(safe(baritoneStatus, snap.baritoneStatus()))
                .aiStatus(safe(aiStatus, snap.aiStatus()))
                .recentEvents(context.recent(12))
                .build();
        monitor.record(record);
        return record;
    }

    private static String safe(Supplier<String> supplier, String fallback) {
        try {
            String value = supplier == null ? null : supplier.get();
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Formats a throwable with its cause chain into a bounded string. */
    public static String stackTraceOf(Throwable error) {
        StringWriter writer = new StringWriter();
        try (PrintWriter print = new PrintWriter(writer)) {
            error.printStackTrace(print);
        }
        return writer.toString();
    }

    /** Convenience for callers that only have a message (e.g. a module's recorded error). */
    public static List<String> frameSummary(Throwable error) {
        return CrashAnalyzer.extractClientFrames(stackTraceOf(error));
    }
}
