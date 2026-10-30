package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Value-semantics tests for {@link BaritoneStatus}, especially the transition key. */
class BaritoneStatusTest {

    @Test
    void unavailableStatusIsNotUsable() {
        BaritoneStatus status = BaritoneStatus.unavailable("no baritone");
        assertFalse(status.available());
        assertFalse(status.usable());
        assertEquals("no baritone", status.reason());
        assertEquals("unavailable", status.stateKey());
    }

    @Test
    void idleStatusKeyIsIdle() {
        BaritoneStatus status = new BaritoneStatus(true, "1.10", "", false, "none", "",
                BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD,
                false, false, false, BaritoneStatus.UNKNOWN, BaritoneStatus.UNKNOWN, false, false, "idle");
        assertEquals("idle", status.stateKey());
    }

    @Test
    void goalKeyIncludesTypePathingAndDetail() {
        BaritoneStatus waiting = new BaritoneStatus(true, "1.10", "", true, "GoalBlock", "1 2 3",
                1, 2, 3, false, false, true, 10, 40, false, false, "block 1 2 3");
        BaritoneStatus pathing = new BaritoneStatus(true, "1.10", "", true, "GoalBlock", "1 2 3",
                1, 2, 3, true, false, true, 10, 40, false, false, "block 1 2 3");
        assertEquals("GoalBlock|waiting|1 2 3", waiting.stateKey());
        assertEquals("GoalBlock|pathing|1 2 3", pathing.stateKey());
    }

    @Test
    void hasGoalPositionRequiresCoordinates() {
        BaritoneStatus noCoords = new BaritoneStatus(true, "1.10", "", true, "GoalBlock", "",
                BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD,
                true, false, true, 10, 40, false, false, "block");
        assertFalse(noCoords.hasGoalPosition());

        BaritoneStatus withCoords = new BaritoneStatus(true, "1.10", "", true, "GoalBlock", "",
                10, 64, -20, true, false, true, 10, 40, false, false, "block");
        assertTrue(withCoords.hasGoalPosition());
    }
}
