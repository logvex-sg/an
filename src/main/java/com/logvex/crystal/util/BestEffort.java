package com.logvex.crystal.util;

import com.logvex.crystal.client.CrystalClient;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs an optional, best-effort action that must never break the caller, while still making
 * a <em>persistent</em> failure visible exactly once.
 *
 * <p>Some code paths are genuinely optional: playing a UI click, drawing a single ESP
 * label, blurring the ClickGUI backdrop, notifying a cosmetic listener. Swallowing their
 * exceptions is correct - the feature is decorative and a broken sound driver or an
 * unsupported blur pipeline must not crash a frame. But swallowing them <em>silently and
 * forever</em> hides real bugs.
 *
 * <p>This helper threads the needle: it catches everything, records the first failure per
 * distinct key to the shared log and {@code ; errors}, and suppresses repeats so a hot
 * render path cannot flood the log. Keys are stable strings (e.g. {@code "gui-blur"}), so
 * "blur is broken on this GPU" is reported once, not once per frame.
 */
public final class BestEffort {

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private BestEffort() {
    }

    /**
     * Runs {@code action}, swallowing any throwable. The first failure for {@code key} is
     * logged with its stack trace and filed under {@code key} in the module error log; later
     * failures with the same key are ignored.
     */
    public static void run(String key, String description, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (REPORTED.add(key)) {
                CrystalClient.LOG.warn("Best-effort {} failed (further '{}' failures suppressed)", description, key, t);
                try {
                    CrystalClient.errors().record(key, t);
                } catch (RuntimeException ignored) {
                    // The error log itself is best-effort during early startup.
                }
            }
        }
    }

    /**
     * Records a failure that was already caught by the caller and deliberately handled (the
     * caller's own {@code catch} stays in place for control flow). Reports the first failure
     * per {@code key} once, then suppresses repeats.
     */
    public static void report(String key, String description, Throwable t) {
        if (t != null && REPORTED.add(key)) {
            CrystalClient.LOG.warn("Best-effort {} failed (further '{}' failures suppressed)", description, key, t);
            try {
                CrystalClient.errors().record(key, t);
            } catch (RuntimeException ignored) {
                // The error log itself is best-effort during early startup.
            }
        }
    }

    /** Test/utility hook: forgets which keys have been reported. */
    public static void resetReported() {
        REPORTED.clear();
    }

    /** True when a failure has already been reported for {@code key}. */
    public static boolean wasReported(String key) {
        return REPORTED.contains(key);
    }
}
