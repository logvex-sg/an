package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.util.waypoint.Waypoint;
import org.junit.jupiter.api.Test;

/** Cross-dimension honesty and goal conversion tests for {@link BaritoneWaypointBridge}. */
class BaritoneWaypointBridgeTest {

    @Test
    void sameDimensionWaypointIsReachable() {
        Waypoint waypoint = new Waypoint("Base", 100, 64, -200, 0xFFFFFFFF, "minecraft:overworld");
        assertTrue(BaritoneWaypointBridge.reachable(waypoint, "minecraft:overworld"));
    }

    @Test
    void otherDimensionWaypointIsNotReachable() {
        Waypoint waypoint = new Waypoint("NetherBase", 10, 64, 10, 0xFFFFFFFF, "minecraft:the_nether");
        assertFalse(BaritoneWaypointBridge.reachable(waypoint, "minecraft:overworld"));
    }

    @Test
    void dimensionAgnosticWaypointMatchesAnywhere() {
        Waypoint waypoint = new Waypoint("Portal", 0, 64, 0, 0xFFFFFFFF, "");
        assertTrue(BaritoneWaypointBridge.reachable(waypoint, "minecraft:overworld"));
        assertTrue(BaritoneWaypointBridge.reachable(waypoint, "minecraft:the_nether"));
    }

    @Test
    void goalForBuildsBlockGoalWithWaypointCoordinates() {
        Waypoint waypoint = new Waypoint("Stash", 123, 70, -456, 0xFFFFFFFF, "minecraft:overworld");
        BaritoneGoal goal = BaritoneWaypointBridge.goalFor(waypoint, "minecraft:overworld");
        assertEquals(BaritoneGoal.Kind.GOAL_BLOCK, goal.kind());
        assertEquals(123, goal.x());
        assertEquals(70, goal.y());
        assertEquals(-456, goal.z());
    }

    @Test
    void goalForRejectsOtherDimensionWaypoint() {
        Waypoint waypoint = new Waypoint("NetherBase", 1, 2, 3, 0xFFFFFFFF, "minecraft:the_nether");
        assertThrows(IllegalArgumentException.class,
                () -> BaritoneWaypointBridge.goalFor(waypoint, "minecraft:overworld"));
    }

    @Test
    void rejectionReasonNamesTheWaypointAndDimension() {
        Waypoint waypoint = new Waypoint("NetherBase", 1, 2, 3, 0xFFFFFFFF, "minecraft:the_nether");
        String reason = BaritoneWaypointBridge.rejectionReason(waypoint);
        assertTrue(reason.contains("NetherBase"));
        assertTrue(reason.contains("minecraft:the_nether"));
    }
}
