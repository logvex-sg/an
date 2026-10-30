package com.logvex.crystal.automation;

/**
 * The services an automation rule may act on, behind a narrow interface.
 *
 * <p>The engine is written against this interface rather than the live client so the whole
 * rule policy - triggers, conditions, action outcomes, loop guards - is unit testable with a
 * fake context, and so the set of things a rule can do is an explicit, auditable list. A rule
 * can never run arbitrary code: it can only invoke one of these named operations.
 *
 * <p>Every mutating method returns whether it took effect, so an action's completion can be
 * verified rather than assumed.
 */
public interface AutomationContext {

    // ------------------------------------------------------------------ facts

    /** True when a player is present. */
    boolean playerAvailable();

    /** True when a world is loaded. */
    boolean worldAvailable();

    /** True when a module with this name is registered. */
    boolean moduleInstalled(String name);

    /** True when a module with this name is enabled. */
    boolean moduleEnabled(String name);

    /** True when an optional integration is available (e.g. {@code "baritone"}). */
    boolean integrationAvailable(String name);

    /** The name of the currently active profile, or empty when none. */
    String activeProfile();

    /** The local player's health as a 0..1 fraction, or 1.0 when unknown. */
    double healthFraction();

    /** Distance to the current combat target, or -1 when there is none. */
    double targetDistance();

    /** True when no client action is currently running (the coordinator is idle). */
    boolean actionIdle();

    // ------------------------------------------------------------------ effects

    /** Enables a module. Returns true when it is enabled afterwards. */
    boolean enableModule(String name);

    /** Disables a module. Returns true when it is disabled afterwards. */
    boolean disableModule(String name);

    /** Applies a named profile. Returns true when it existed and was applied. */
    boolean switchProfile(String name);

    /** Cancels the current Baritone route, if any. */
    boolean cancelRoute();

    /** Posts a notification to the user. */
    void notify(String message, String level);

    /** Records a line in the local diagnostics log. */
    void log(String message);
}
