package com.logvex.crystal.util.waypoint;

import java.util.Locale;

/**
 * Pure navigation maths over waypoints: the compass bearing and distance from the player to
 * a waypoint, and whether the waypoint is even in the current dimension.
 *
 * <p>It is deliberately honest about what it cannot know. A waypoint in another dimension is
 * reported as a mismatch rather than silently shown at its raw coordinates, because the
 * Overworld/Nether 8:1 ratio means a raw coordinate is not a valid route. Nothing here claims
 * a path is walkable; it only reports direction and straight-line distance.
 */
public final class Navigation {

    /** The eight compass sectors, in order starting from north. */
    public enum Bearing {
        N("N"), NE("NE"), E("E"), SE("SE"), S("S"), SW("SW"), W("W"), NW("NW");

        private final String label;

        Bearing(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private Navigation() {
    }

    /**
     * The compass bearing from one point to another.
     *
     * <p>Minecraft axes: {@code +X} is east and {@code +Z} is south, so north is {@code -Z}.
     * The result is one of the eight 45-degree sectors.
     */
    public static Bearing bearing(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx == 0 && dz == 0) {
            return Bearing.N;
        }
        // atan2(east, north) gives 0 for due north, 90 for due east, in degrees.
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.round(degrees / 45.0) & 7;
        return Bearing.values()[sector];
    }

    /** Straight-line horizontal distance, ignoring height. */
    public static double horizontalDistance(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** True when the waypoint belongs to the given dimension (or to every dimension). */
    public static boolean dimensionMatches(Waypoint waypoint, String dimension) {
        return waypoint != null && waypoint.matchesDimension(dimension);
    }

    /**
     * A short, honest summary: {@code "<bearing> <distance>m"} when the waypoint is in this
     * dimension, or {@code "<dimension> only"} when it is not (so the player is never told a
     * cross-dimension waypoint is a straight-line route).
     */
    public static String describe(Waypoint waypoint, double fromX, double fromY, double fromZ, String dimension) {
        if (waypoint == null) {
            return "?";
        }
        if (!waypoint.matchesDimension(dimension)) {
            String dim = waypoint.dimension().isEmpty() ? "other dimension" : waypoint.dimension();
            return dim + " only";
        }
        Bearing bearing = bearing(fromX, fromZ, waypoint.x(), waypoint.z());
        double distance = horizontalDistance(fromX, fromZ, waypoint.x(), waypoint.z());
        return bearing.label() + " " + Math.round(distance) + "m";
    }

    /**
     * A relative direction ("ahead", "left", ...) given the player's yaw and the target
     * bearing. Minecraft yaw 0 faces south (+Z), increasing yaw turns west.
     */
    public static String relative(double yaw, Bearing target) {
        double radians = Math.toRadians(yaw);
        // Look vector: x = -sin(yaw), z = cos(yaw).
        Bearing facing = bearing(0, 0, -Math.sin(radians), Math.cos(radians));
        int delta = (target.ordinal() - facing.ordinal() + 8) % 8;
        return switch (delta) {
            case 0 -> "ahead";
            case 1, 7 -> "slightly ahead";
            case 2, 6 -> "to the side";
            case 3, 5 -> "slightly behind";
            default -> "behind";
        };
    }

    /** Normalises a dimension id for comparison, e.g. {@code minecraft:overworld}. */
    public static String normalizeDimension(String dimension) {
        return dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
    }
}
