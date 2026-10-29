package com.logvex.crystal.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The set of named capabilities the client offers to extensions, with a snapshot of which are
 * currently active.
 *
 * <p>A capability is a promise that a named operation is available <em>right now</em>. It is
 * distinct from an extension's declared capability list in its manifest: a manifest says "I
 * provide/require X"; the registry says "X is live". A capability such as {@link #BARITONE}
 * is only active when the Baritone mod is actually present, so an extension must ask the
 * registry rather than assume.
 */
public final class Capabilities {

    /** Combat target snapshots and combat state queries. */
    public static final String COMBAT = "combat";
    /** Read-only world state (dimension, position, biome where available). */
    public static final String WORLD = "world";
    /** Waypoint storage and navigation helpers. */
    public static final String WAYPOINTS = "waypoints";
    /** HUD element registration. */
    public static final String HUD = "hud";
    /** Chat command registration. */
    public static final String COMMANDS = "commands";
    /** Event stream subscription. */
    public static final String EVENTS = "events";
    /** Module registration. */
    public static final String MODULES = "modules";
    /** Notification display. */
    public static final String NOTIFICATIONS = "notifications";
    /** Module error log and diagnostics counters. */
    public static final String DIAGNOSTICS = "diagnostics";
    /** Optional Baritone navigation, present only when Baritone is installed. */
    public static final String BARITONE = "baritone";

    private final Set<String> active;

    public Capabilities(Set<String> active) {
        this.active = Collections.unmodifiableSet(new LinkedHashSet<>(active));
    }

    /** True when the named capability is currently available. */
    public boolean has(String name) {
        return name != null && active.contains(name);
    }

    /** The active capability names. */
    public Set<String> active() {
        return active;
    }

    @Override
    public String toString() {
        return "Capabilities" + active;
    }
}
