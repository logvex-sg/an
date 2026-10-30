package com.logvex.crystal.event;

/**
 * The kinds of observable client-side state change the event bus carries.
 *
 * <p>Each kind is something the client can actually observe from data the server already sent
 * it. Where a change cannot be observed through an event (for example a remote player's exact
 * health between damage ticks), the bus carries a coarse "changed" event rather than inventing
 * precision the client does not have.
 */
public enum EventKind {

    /** A world became loaded (join or respawn). */
    WORLD_JOINED,
    /** The world was unloaded (disconnect). */
    WORLD_LEFT,
    /** The player changed dimension. */
    DIMENSION_CHANGED,
    /** An entity entered the shared tracking snapshot. */
    ENTITY_SPAWNED,
    /** An entity left the shared tracking snapshot. */
    ENTITY_DESPAWNED,
    /** The current combat target changed (set, cleared or replaced). */
    TARGET_CHANGED,
    /** A tracked entity's health changed between snapshots. */
    ENTITY_HEALTH_CHANGED,
    /** A tracked entity's armour points changed between snapshots. */
    ENTITY_ARMOR_CHANGED,
    /** Baritone's navigation state changed (goal set/cleared, pathing started/stopped). */
    BARITONE_STATE_CHANGED,
    /** A baritone goal the client issued completed. */
    BARITONE_GOAL_REACHED,
    /** The player arrived within range of a waypoint. */
    WAYPOINT_REACHED,
    /** A module was enabled or disabled. */
    MODULE_STATE_CHANGED,
    /** A named profile was applied. */
    PROFILE_APPLIED,
    /** The player's own health crossed a change threshold. */
    PLAYER_HEALTH_CHANGED,
    /** A monitored condition became true (used by the automation engine). */
    CONDITION_MET,
    /** A manually raised event, e.g. from a command. */
    MANUAL
}
