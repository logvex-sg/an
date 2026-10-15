package com.logvex.crystal.util.waypoint;

import java.util.Locale;

/**
 * A saved position in the world, with an optional dimension filter.
 *
 * <p>Waypoints are an anarchy essential: with no minimap mod they are how a player
 * remembers a base, a stash or a portal. Immutable so the render thread can read one while
 * the command thread edits the store.
 *
 * @param name      user-supplied label
 * @param x         block x
 * @param y         block y
 * @param z         block z
 * @param color     ARGB colour used by the ESP marker
 * @param dimension dimension id the waypoint belongs to, or empty for "any"
 */
public record Waypoint(String name, int x, int y, int z, int color, String dimension) {

    public Waypoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("waypoint name must not be blank");
        }
        dimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
    }

    /** True when this waypoint should be shown in the given dimension. */
    public boolean matchesDimension(String currentDimension) {
        return dimension.isEmpty() || dimension.equalsIgnoreCase(currentDimension);
    }

    /** Squared distance from a point, for sorting nearest-first. */
    public double distanceSquaredTo(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
