package com.logvex.crystal.integration.baritone;

import com.logvex.crystal.util.waypoint.Waypoint;
import java.util.Locale;

/**
 * Pure conversion of a saved {@link Waypoint} into a Baritone goal, with the same
 * cross-dimension honesty the rest of the navigation code follows.
 *
 * <p>A waypoint in another dimension is <em>not</em> turned into a straight-line goal: the
 * Overworld/Nether 8:1 ratio means the raw coordinates are not a route. Instead the caller is
 * told the waypoint is unreachable from here, exactly as {@code Navigation.describe} reports.
 */
public final class BaritoneWaypointBridge {

    /** Why a waypoint cannot be routed to, or {@code null} when it can. */
    public enum Rejection {
        NONE,
        WRONG_DIMENSION
    }

    private BaritoneWaypointBridge() {
    }

    /** True when {@code waypoint} is in the player's current dimension (or matches any). */
    public static boolean reachable(Waypoint waypoint, String currentDimension) {
        return waypoint != null && waypoint.matchesDimension(currentDimension);
    }

    /**
     * Builds a block goal for {@code waypoint}.
     *
     * @throws IllegalArgumentException when the waypoint is in another dimension
     */
    public static BaritoneGoal goalFor(Waypoint waypoint, String currentDimension) {
        if (waypoint == null) {
            throw new IllegalArgumentException("waypoint must not be null");
        }
        if (!reachable(waypoint, currentDimension)) {
            throw new IllegalArgumentException("waypoint '" + waypoint.name() + "' is in "
                    + (waypoint.dimension().isEmpty() ? "another dimension" : waypoint.dimension())
                    + ", not " + describeDimension(currentDimension));
        }
        return BaritoneGoal.block(waypoint.x(), waypoint.y(), waypoint.z());
    }

    /** A short reason string for a rejected waypoint, for the notification. */
    public static String rejectionReason(Waypoint waypoint) {
        if (waypoint == null) {
            return "no waypoint";
        }
        String dim = waypoint.dimension().isEmpty() ? "another dimension" : waypoint.dimension();
        return "'" + waypoint.name() + "' is in " + dim + " only";
    }

    private static String describeDimension(String dimension) {
        return dimension == null || dimension.isBlank() ? "this dimension" : dimension.toLowerCase(Locale.ROOT);
    }
}
