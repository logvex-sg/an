package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Validation and factory tests for {@link BaritoneGoal}. */
class BaritoneGoalTest {

    @Test
    void blockGoalKeepsCoordinates() {
        BaritoneGoal goal = BaritoneGoal.block(10, 64, -20);
        assertEquals(BaritoneGoal.Kind.GOAL_BLOCK, goal.kind());
        assertEquals(10, goal.x());
        assertEquals(64, goal.y());
        assertEquals(-20, goal.z());
        assertEquals("block 10 64 -20", goal.describe());
    }

    @Test
    void xzGoalIgnoresHeight() {
        BaritoneGoal goal = BaritoneGoal.xz(5, 7);
        assertEquals(BaritoneGoal.Kind.GOAL_XZ, goal.kind());
        assertEquals(0, goal.y());
        assertEquals("xz 5 7", goal.describe());
    }

    @Test
    void nearGoalRequiresPositiveRadius() {
        assertThrows(IllegalArgumentException.class, () -> BaritoneGoal.near(0, 0, 0, 0));
        BaritoneGoal goal = BaritoneGoal.near(1, 2, 3, 4);
        assertEquals(4, goal.range());
        assertEquals("near 1 2 3 r4", goal.describe());
    }

    @Test
    void coordinatesOutsideWorldBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BaritoneGoal.block(40_000_000, 64, 0));
    }

    @Test
    void nullKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BaritoneGoal(null, 0, 0, 0, 1));
    }

    @Test
    void goalKindsMapToBaritoneClasses() {
        assertEquals("GoalBlock", BaritoneGoal.Kind.GOAL_BLOCK.baritoneClass());
        assertEquals("GoalNear", BaritoneGoal.Kind.GOAL_NEAR.baritoneClass());
        assertEquals("GoalXZ", BaritoneGoal.Kind.GOAL_XZ.baritoneClass());
        assertTrue(BaritoneGoal.Kind.GOAL_XZ.needsXZ());
        assertFalse(BaritoneGoal.Kind.GOAL_SPAWN.needsXZ());
    }
}
