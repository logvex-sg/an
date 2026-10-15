package com.logvex.crystal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SessionStats}. The service is a singleton, so each test resets it; the
 * no-world path ({@code tick(null)}) is exercised directly and must never accumulate time
 * or throw.
 */
class SessionStatsTest {

    @Test
    void resetZeroesEveryCounter() {
        SessionStats stats = SessionStats.get();
        stats.reset();
        assertEquals(0, stats.activeMillis());
        assertEquals(0.0, stats.distanceWalked(), 1e-9);
        assertEquals(0.0, stats.maxDistanceFromOrigin(), 1e-9);
        assertNull(stats.origin());
    }

    @Test
    void tickWithNoWorldDoesNotAccumulateTime() {
        SessionStats stats = SessionStats.get();
        stats.reset();
        for (int i = 0; i < 5; i++) {
            stats.tick(null);
        }
        assertEquals(0, stats.activeMillis());
        assertEquals(0.0, stats.distanceWalked(), 1e-9);
    }

    @Test
    void firstTickInitialisesTheSession() {
        SessionStats stats = SessionStats.get();
        stats.reset();
        stats.tick(null);
        // The session start is stamped even without a world, so a later world tick counts.
        assertTrue(stats.activeMillis() >= 0);
    }
}
