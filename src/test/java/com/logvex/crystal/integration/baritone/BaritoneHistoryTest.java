package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Bounded-buffer and dedup tests for {@link BaritoneHistory}. */
class BaritoneHistoryTest {

    private static BaritoneStatus idle() {
        return new BaritoneStatus(true, "1.10", "", false, "none", "",
                BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD,
                false, false, false, BaritoneStatus.UNKNOWN, BaritoneStatus.UNKNOWN, false, false, "idle");
    }

    private static BaritoneStatus pathing(String detail) {
        return new BaritoneStatus(true, "1.10", "", true, "GoalBlock", detail,
                10, 64, -20, true, false, true, 40, 200, false, false, "block " + detail);
    }

    @Test
    void firstObservationIsRecorded() {
        BaritoneHistory history = new BaritoneHistory(8);
        assertTrue(history.observe(idle(), 1_000));
        assertEquals(1, history.size());
    }

    @Test
    void repeatedIdenticalStatesAreDeduplicated() {
        BaritoneHistory history = new BaritoneHistory(8);
        history.observe(pathing("10 64 -20"), 1_000);
        assertFalse(history.observe(pathing("10 64 -20"), 1_100));
        assertFalse(history.observe(pathing("10 64 -20"), 1_200));
        assertEquals(1, history.size());
    }

    @Test
    void stateChangeIsRecorded() {
        BaritoneHistory history = new BaritoneHistory(8);
        history.observe(idle(), 1_000);
        assertTrue(history.observe(pathing("1 2 3"), 1_100));
        assertEquals(2, history.size());
    }

    @Test
    void capacityEvictsOldest() {
        BaritoneHistory history = new BaritoneHistory(3);
        history.observe(pathing("1 2 3"), 1);
        history.observe(pathing("4 5 6"), 2);
        history.observe(pathing("7 8 9"), 3);
        history.observe(idle(), 4);
        assertEquals(3, history.size());
        assertEquals("idle", history.recent(3).get(0).detail());
    }

    @Test
    void errorsAreAlwaysRecorded() {
        BaritoneHistory history = new BaritoneHistory(4);
        history.error("boom", 1);
        history.error("boom", 2);
        assertEquals(2, history.size());
        assertEquals("error", history.recent(1).get(0).kind());
    }

    @Test
    void recentReturnsNewestFirstUpToLimit() {
        BaritoneHistory history = new BaritoneHistory(8);
        history.observe(pathing("1 1 1"), 1);
        history.observe(pathing("2 2 2"), 2);
        history.observe(pathing("3 3 3"), 3);
        assertEquals(2, history.recent(2).size());
        assertEquals("GoalBlock 3 3 3 (pathing)", history.recent(2).get(0).detail());
    }

    @Test
    void clearResetsEverything() {
        BaritoneHistory history = new BaritoneHistory(4);
        history.observe(idle(), 1);
        history.clear();
        assertEquals(0, history.size());
        assertEquals("<none>", history.lastKey());
    }
}
