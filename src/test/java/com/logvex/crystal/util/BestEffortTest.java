package com.logvex.crystal.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BestEffort}: it must swallow failures, report each key once, and never
 * propagate. The shared reported-key set is cleared between tests.
 */
class BestEffortTest {

    @AfterEach
    void clearKeys() {
        BestEffort.resetReported();
    }

    @Test
    void swallowsTheFailure() {
        assertDoesNotThrow(() -> BestEffort.run("k1", "thing", () -> {
            throw new IllegalStateException("boom");
        }));
        assertTrue(BestEffort.wasReported("k1"));
    }

    @Test
    void reportsEachKeyOnlyOnce() {
        BestEffort.run("k2", "thing", () -> {
            throw new IllegalStateException("one");
        });
        assertTrue(BestEffort.wasReported("k2"));
        // A second failure with the same key is swallowed and stays reported.
        BestEffort.run("k2", "thing", () -> {
            throw new IllegalStateException("two");
        });
        assertTrue(BestEffort.wasReported("k2"));
    }

    @Test
    void successDoesNotMarkTheKey() {
        BestEffort.run("k3", "thing", () -> {
        });
        assertFalse(BestEffort.wasReported("k3"));
    }

    @Test
    void reportHandlesAnAlreadyCaughtThrowable() {
        BestEffort.report("k4", "thing", new RuntimeException("x"));
        assertTrue(BestEffort.wasReported("k4"));
        // A null throwable is a no-op.
        BestEffort.report("k5", "thing", null);
        assertFalse(BestEffort.wasReported("k5"));
    }
}
