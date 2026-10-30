package com.logvex.crystal.ai.crash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.ai.context.DiagnosticContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Crash capture, deduplication, handler chaining and analysis. */
class CrashCaptureTest {

    private DiagnosticContext context() {
        DiagnosticContext context = new DiagnosticContext(16);
        context.setSnapshot(new DiagnosticContext.Snapshot(
                "6.0.0", "1.21.11", "0.19.5", "21.0.1", "Linux", "available", "disabled"));
        return context;
    }

    @Test
    void capturesJavaExceptionWithEnvironment() {
        CrashMonitor monitor = new CrashMonitor(10);
        CrashCapture capture = new CrashCapture(monitor, context(), () -> true,
                () -> "available", () -> "disabled");
        Throwable error = new IllegalStateException("boom");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.logvex.crystal.module.render.BlockEspModule", "render",
                        "BlockEspModule.java", 12)});

        CrashRecord record = capture.capture("thread:Client", error);

        assertNotNull(record);
        assertEquals("java.lang.IllegalStateException", record.exceptionType());
        assertEquals("boom", record.exceptionMessage());
        assertEquals("6.0.0", record.modVersion());
        assertEquals("1.21.11", record.minecraftVersion());
        assertEquals("available", record.baritoneStatus());
        assertTrue(record.rawStackTrace().contains("BlockEspModule"));
        assertEquals(1, monitor.size());
    }

    @Test
    void captureIsDisabledWhenSettingOff() {
        CrashMonitor monitor = new CrashMonitor(10);
        CrashCapture capture = new CrashCapture(monitor, context(), () -> false,
                () -> "available", () -> "disabled");
        assertNull(capture.capture("x", new RuntimeException("x")));
        assertEquals(0, monitor.size());
    }

    @Test
    void duplicateCrashIsCountedNotRepeated() {
        CrashMonitor monitor = new CrashMonitor(10);
        CrashCapture capture = new CrashCapture(monitor, context(), () -> true, () -> "?", () -> "?");
        Throwable first = new RuntimeException("tick failed at 1000 ms");
        Throwable second = new RuntimeException("tick failed at 2000 ms");
        assertNotNull(capture.capture("a", first));
        assertNotNull(capture.capture("b", second));
        // Same shape -> same fingerprint -> one history entry, seen twice.
        assertEquals(1, monitor.size());
        assertEquals(2, monitor.seenCount(CrashFingerprint.of(first)));
    }

    @Test
    void moduleExceptionCaptureIsClassified() {
        CrashMonitor monitor = new CrashMonitor(10);
        CrashCapture capture = new CrashCapture(monitor, context(), () -> true, () -> "?", () -> "?");
        Throwable error = new RuntimeException("npe");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.logvex.crystal.module.combat.SurroundModule", "onTick",
                        "SurroundModule.java", 5)});
        CrashRecord record = capture.capture("module:Surround", error);
        assertNotNull(record);
        assertEquals("SurroundModule", record.suspectedModule());
    }

    @Test
    void installChainsToPreviousHandlerAndRestores() {
        AtomicReference<Throwable> seenByPrevious = new AtomicReference<>();
        Thread.UncaughtExceptionHandler original = (thread, error) -> seenByPrevious.set(error);
        Thread.UncaughtExceptionHandler saved = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(original);
        try {
            CrashMonitor monitor = new CrashMonitor(10);
            CrashCapture capture = new CrashCapture(monitor, context(), () -> true, () -> "?", () -> "?");
            capture.install();
            Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
            assertNotNull(installed);
            assertFalse(installed == original, "our handler must be installed");

            Throwable error = new RuntimeException("uncaught");
            installed.uncaughtException(Thread.currentThread(), error);

            // Our capture ran, and the original handler still saw the exception.
            assertEquals(1, monitor.size());
            assertEquals(error, seenByPrevious.get());

            capture.uninstall();
            assertTrue(Thread.getDefaultUncaughtExceptionHandler() == original);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(saved);
        }
    }

    @Test
    void handlerFailureIsIsolatedFromTheOriginalHandler() {
        AtomicBoolean previousCalled = new AtomicBoolean();
        Thread.UncaughtExceptionHandler original = (thread, error) -> previousCalled.set(true);
        Thread.UncaughtExceptionHandler saved = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(original);
        try {
            // A capture whose own logic throws must not stop the original handler from running.
            CrashMonitor monitor = new CrashMonitor(10);
            CrashCapture capture = new CrashCapture(monitor, context(), () -> {
                throw new RuntimeException("boom in capture");
            }, () -> "?", () -> "?");
            capture.install();
            Thread.getDefaultUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), new RuntimeException("x"));
            assertTrue(previousCalled.get(), "original handler must still run");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(saved);
        }
    }

    @Test
    void monitorNotifiesListenerOncePerNewFingerprint() {
        CrashMonitor monitor = new CrashMonitor(10);
        List<String> notified = new ArrayList<>();
        monitor.addListener(record -> notified.add(record.fingerprint()));
        CrashRecord a = CrashRecord.builder().fingerprint("aaa").build();
        CrashRecord b = CrashRecord.builder().fingerprint("aaa").build();
        assertTrue(monitor.record(a));
        assertFalse(monitor.record(b));
        assertEquals(1, notified.size());
    }

    @Test
    void monitorHistoryIsBounded() {
        CrashMonitor monitor = new CrashMonitor(3);
        for (int i = 0; i < 10; i++) {
            monitor.record(CrashRecord.builder().fingerprint("fp" + i).build());
        }
        assertEquals(3, monitor.size());
    }

    @Test
    void analyzerExplainsOutOfMemoryAsConfirmed() {
        CrashRecord record = CrashRecord.builder()
                .fingerprint("x").exceptionType("java.lang.OutOfMemoryError")
                .exceptionMessage("Java heap space").category(CrashCategory.OUT_OF_MEMORY)
                .severity(CrashSeverity.CRITICAL).source("thread:Client").build();
        Diagnosis d = CrashAnalyzer.analyze(record);
        assertEquals(Diagnosis.Confidence.CONFIRMED, d.confidence());
        assertFalse(d.suggestedFixes().isEmpty());
        assertFalse(d.aiGenerated());
    }

    @Test
    void analyzerNeverPresentsUnknownAsConfirmed() {
        CrashRecord record = CrashRecord.builder()
                .fingerprint("y").exceptionType("java.lang.Throwable").exceptionMessage("weird")
                .category(CrashCategory.UNKNOWN).source("thread:Client").build();
        Diagnosis d = CrashAnalyzer.analyze(record);
        assertEquals(Diagnosis.Confidence.POSSIBLE, d.confidence());
        assertTrue(d.likelyCause().toLowerCase().contains("not confirmed"));
    }

    @Test
    void analyzerExtractsClientFrames() {
        CrashRecord record = CrashRecord.builder()
                .fingerprint("z").exceptionType("java.lang.RuntimeException").exceptionMessage("x")
                .rawStackTrace("at com.logvex.crystal.module.render.BlockEspModule.render(BlockEspModule.java:9)\n"
                        + "at com.logvex.crystal.ai.MiniAIManager.tick(MiniAIManager.java:1)")
                .category(CrashCategory.MODULE_EXCEPTION).source("module:BlockEsp").build();
        Diagnosis d = CrashAnalyzer.analyze(record);
        assertFalse(d.keyFrames().isEmpty());
        assertTrue(d.keyFrames().get(0).contains("BlockEspModule"));
        assertTrue(d.keyFrames().stream().noneMatch(f -> f.contains("MiniAIManager")),
                "AI frames are excluded from the report");
    }
}
