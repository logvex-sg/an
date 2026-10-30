package com.logvex.crystal.integration.baritone;

/**
 * An immutable snapshot of Baritone's navigation state, as the client last observed it.
 *
 * <p>This is deliberately a plain value type: it carries no Minecraft or Baritone types, so it
 * can be produced by the reflective bridge on the client thread and read by the HUD, the GUI
 * and unit tests without touching the live game. Every field is either a verified fact read
 * from Baritone's public API, or an explicit "unknown" sentinel - never a guess.
 *
 * @param available     true when the Baritone API was detected and a call succeeded
 * @param version       the installed Baritone version, or {@code "unknown"}
 * @param reason        why integration is unavailable (empty when {@code available})
 * @param hasGoal       whether a goal is currently set
 * @param goalType      simple class name of the active goal (e.g. {@code GoalBlock}), or {@code "none"}
 * @param goalDetail    a short human description of the goal (e.g. {@code "120 64 -40"}), or empty
 * @param goalX         goal block x, or {@code Integer.MIN_VALUE} when not exposed
 * @param goalY         goal block y, or {@code Integer.MIN_VALUE} when not exposed
 * @param goalZ         goal block z, or {@code Integer.MIN_VALUE} when not exposed
 * @param pathing       whether Baritone is currently following a path
 * @param calculating   whether a path calculation is in progress
 * @param hasPath       whether a usable path currently exists
 * @param pathLength    number of nodes in the current path, or {@code -1} when unknown
 * @param ticksToGoal   estimated ticks to the goal, or {@code -1} when unknown/not exposed
 * @param paused        whether Baritone reports the current command is a pause request
 * @param safeToCancel  whether Baritone reports it is safe to cancel, or {@code false} when unknown
 * @param statusText    a one-line human summary suitable for the HUD
 */
public record BaritoneStatus(
        boolean available,
        String version,
        String reason,
        boolean hasGoal,
        String goalType,
        String goalDetail,
        int goalX,
        int goalY,
        int goalZ,
        boolean pathing,
        boolean calculating,
        boolean hasPath,
        int pathLength,
        int ticksToGoal,
        boolean paused,
        boolean safeToCancel,
        String statusText) {

    /** Sentinel for every numeric field that Baritone does not expose. */
    public static final int UNKNOWN = -1;

    /** Sentinel for a goal coordinate that Baritone does not expose. */
    public static final int NO_COORD = Integer.MIN_VALUE;

    /** The status used before the first poll, or when Baritone is absent. */
    public static BaritoneStatus unavailable(String reason) {
        return new BaritoneStatus(false, "unknown", reason == null ? "" : reason,
                false, "none", "", NO_COORD, NO_COORD, NO_COORD,
                false, false, false, UNKNOWN, UNKNOWN, false, false,
                "Baritone unavailable");
    }

    /** True when Baritone is installed and its API responded. */
    public boolean usable() {
        return available;
    }

    /** True when a goal position is known and can be drawn as a marker. */
    public boolean hasGoalPosition() {
        return hasGoal && goalX != NO_COORD && goalZ != NO_COORD;
    }

    /**
     * A stable state key used to detect transitions (goal set/cleared, path start/stop). Two
     * snapshots with the same key describe the same navigation state, so the service only
     * records history and fires notifications when this changes.
     */
    public String stateKey() {
        if (!available) {
            return "unavailable";
        }
        if (!hasGoal) {
            return "idle";
        }
        return goalType + "|" + (pathing ? "pathing" : "waiting") + "|" + goalDetail;
    }
}
