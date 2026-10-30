package com.logvex.crystal.ai.crash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fingerprint stability and the deterministic classifier. */
class CrashFingerprintTest {

    @Test
    void sameExceptionShapeGivesSameFingerprint() {
        RuntimeException a = boom("Failed to tick at 1234 ms");
        RuntimeException b = boom("Failed to tick at 98765 ms");
        // The volatile number is normalised away, so these are the same crash.
        assertEquals(CrashFingerprint.of(a), CrashFingerprint.of(b));
    }

    @Test
    void differentExceptionTypeGivesDifferentFingerprint() {
        assertNotEquals(CrashFingerprint.of(new IllegalStateException("x")),
                CrashFingerprint.of(new IllegalArgumentException("x")));
    }

    @Test
    void fingerprintIgnoresLineNumbers() {
        Throwable t = new IllegalStateException("boom");
        t.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.logvex.crystal.Module", "tick", "Module.java", 10)});
        Throwable u = new IllegalStateException("boom");
        u.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.logvex.crystal.Module", "tick", "Module.java", 999)});
        assertEquals(CrashFingerprint.of(t), CrashFingerprint.of(u));
    }

    @Test
    void fingerprintIsStableAndNonEmpty() {
        String fp = CrashFingerprint.of(new RuntimeException("x"));
        assertEquals(16, fp.length());
        assertFalse(fp.isBlank());
    }

    @Test
    void nullThrowableDoesNotExplode() {
        assertFalse(CrashFingerprint.of(null).isBlank());
    }

    @Test
    void normalisationCollapsesVolatileTokens() {
        assertEquals("path", CrashFingerprint.normalize("/home/someone/thing.txt"));
        assertFalse(CrashFingerprint.normalize("0xdeadbeef").contains("deadbeef"));
    }

    private static RuntimeException boom(String message) {
        RuntimeException e = new RuntimeException(message);
        e.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.logvex.crystal.module.combat.AutoCrystalModule", "onTick",
                        "AutoCrystalModule.java", 42),
                new StackTraceElement("com.logvex.crystal.module.Module", "tickSafely",
                        "Module.java", 100)});
        return e;
    }

    @Test
    void classifiesOutOfMemoryAsCritical() {
        CrashClassifier.Result r = CrashClassifier.classifyText("java.lang.OutOfMemoryError: Java heap space");
        assertEquals(CrashCategory.OUT_OF_MEMORY, r.category());
        assertEquals(CrashSeverity.CRITICAL, r.severity());
    }

    @Test
    void classifiesRenderDriverFailures() {
        CrashClassifier.Result r = CrashClassifier.classifyText(
                "org.lwjgl.opengl.GL11.glDrawArrays OpenGL error 1282");
        assertEquals(CrashCategory.RENDER_DRIVER, r.category());
        assertEquals(CrashSeverity.HIGH, r.severity());
    }

    @Test
    void classifiesModIncompatibility() {
        CrashClassifier.Result r = CrashClassifier.classifyText(
                "net.fabricmc.loader.impl.FormattedException: Mod resolution encountered an incompatible mod set");
        assertEquals(CrashCategory.MOD_INCOMPATIBILITY, r.category());
    }

    @Test
    void classifiesBaritoneFailures() {
        assertEquals(CrashCategory.BARITONE,
                CrashClassifier.classifyText("baritone.api.BaritoneException: pause failed").category());
    }

    @Test
    void classifiesConfigFailures() {
        assertEquals(CrashCategory.CONFIG,
                CrashClassifier.classifyText("com.google.gson.JsonSyntaxException: Malformed JSON").category());
    }

    @Test
    void identifiesSuspectedModuleFromClientFrame() {
        CrashClassifier.Result r = CrashClassifier.classifyText(
                "at com.logvex.crystal.module.render.BlockEspModule#render(BlockEspModule.java:88)");
        assertEquals(CrashCategory.MODULE_EXCEPTION, r.category());
        assertEquals("BlockEspModule", r.suspectedModule());
    }

    @Test
    void doesNotBlameInfrastructurePackages() {
        String module = CrashClassifier.moduleIn("at com.logvex.crystal.gui.ClickGui#render");
        assertEquals("", module);
    }

    @Test
    void unknownFailureIsNotMisclassified() {
        assertEquals(CrashCategory.UNKNOWN,
                CrashClassifier.classifyText("something entirely unexpected happened").category());
    }
}
