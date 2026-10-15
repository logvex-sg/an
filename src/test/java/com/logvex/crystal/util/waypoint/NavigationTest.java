package com.logvex.crystal.util.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for {@link Navigation}: bearings, distances and dimension honesty. */
class NavigationTest {

    @Test
    void cardinalBearingsUseMinecraftAxes() {
        // +X is east, +Z is south, so north is -Z.
        assertEquals(Navigation.Bearing.N, Navigation.bearing(0, 0, 0, -10));
        assertEquals(Navigation.Bearing.E, Navigation.bearing(0, 0, 10, 0));
        assertEquals(Navigation.Bearing.S, Navigation.bearing(0, 0, 0, 10));
        assertEquals(Navigation.Bearing.W, Navigation.bearing(0, 0, -10, 0));
    }

    @Test
    void diagonalBearings() {
        assertEquals(Navigation.Bearing.NE, Navigation.bearing(0, 0, 10, -10));
        assertEquals(Navigation.Bearing.SW, Navigation.bearing(0, 0, -10, 10));
        assertEquals(Navigation.Bearing.SE, Navigation.bearing(0, 0, 10, 10));
        assertEquals(Navigation.Bearing.NW, Navigation.bearing(0, 0, -10, -10));
    }

    @Test
    void coincidentPointsReportNorth() {
        assertEquals(Navigation.Bearing.N, Navigation.bearing(5, 5, 5, 5));
    }

    @Test
    void horizontalDistanceIgnoresHeight() {
        assertEquals(5.0, Navigation.horizontalDistance(0, 0, 3, 4), 1e-9);
    }

    @Test
    void dimensionMismatchIsReportedHonestly() {
        Waypoint nether = new Waypoint("portal", 0, 64, 0, 0xFFFFFFFF, "minecraft:the_nether");
        assertTrue(Navigation.dimensionMatches(nether, "minecraft:the_nether"));
        assertFalse(Navigation.dimensionMatches(nether, "minecraft:overworld"));
        String described = Navigation.describe(nether, 0, 64, 0, "minecraft:overworld");
        assertEquals("minecraft:the_nether only", described);
    }

    @Test
    void anyDimensionWaypointMatchesEverywhere() {
        Waypoint any = new Waypoint("spawn", 0, 64, 0, 0xFFFFFFFF, "");
        assertTrue(Navigation.dimensionMatches(any, "minecraft:overworld"));
        assertTrue(Navigation.dimensionMatches(any, "minecraft:the_end"));
    }

    @Test
    void describeShowsBearingAndDistanceInDimension() {
        Waypoint here = new Waypoint("base", 100, 64, 0, 0xFFFFFFFF, "minecraft:overworld");
        String described = Navigation.describe(here, 0, 64, 0, "minecraft:overworld");
        assertEquals("E 100m", described);
    }

    @Test
    void relativeDirectionUsesPlayerYaw() {
        // Yaw 0 faces south (+Z), so a target due south is "ahead" and due north is "behind".
        assertEquals("ahead", Navigation.relative(0, Navigation.Bearing.S));
        assertEquals("behind", Navigation.relative(0, Navigation.Bearing.N));
        // Yaw 90 faces west (-X).
        assertEquals("ahead", Navigation.relative(90, Navigation.Bearing.W));
    }
}
