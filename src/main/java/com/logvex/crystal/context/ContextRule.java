package com.logvex.crystal.context;

import java.util.Locale;

/**
 * One opt-in rule that applies a named profile when the current situation matches.
 *
 * <p>A rule has an optional dimension (exact id or empty for "any"), an optional server
 * address substring (empty for "any"), and an optional low-health threshold (0 for "any").
 * All non-empty conditions must hold; a rule with no conditions never matches, so an
 * empty rule can never silently apply a profile everywhere.
 *
 * <p>Rules are data only. Applying a profile is the caller's job, and the caller is
 * responsible for the guard rails (never in combat, a cooldown between switches) - this
 * class only answers "does this situation match".
 */
public final class ContextRule {

    private final String dimension;
    private final String serverContains;
    private final double healthBelow;

    public ContextRule(String dimension, String serverContains, double healthBelow) {
        this.dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
        this.serverContains = serverContains == null ? "" : serverContains.trim().toLowerCase(Locale.ROOT);
        this.healthBelow = Math.max(0, healthBelow);
    }

    public String dimension() {
        return dimension;
    }

    public String serverContains() {
        return serverContains;
    }

    public double healthBelow() {
        return healthBelow;
    }

    /** True when the rule specifies at least one condition. */
    public boolean hasConditions() {
        return !dimension.isEmpty() || !serverContains.isEmpty() || healthBelow > 0;
    }

    /** True when every configured condition holds for {@code snapshot}. */
    public boolean matches(ContextSnapshot snapshot) {
        if (snapshot == null || !hasConditions() || !snapshot.playerAlive()) {
            return false;
        }
        if (!dimension.isEmpty()
                && !snapshot.dimension().toLowerCase(Locale.ROOT).equals(dimension)) {
            return false;
        }
        if (!serverContains.isEmpty()
                && !snapshot.serverAddress().toLowerCase(Locale.ROOT).contains(serverContains)) {
            return false;
        }
        if (healthBelow > 0 && snapshot.healthFraction() >= healthBelow) {
            return false;
        }
        return true;
    }
}
