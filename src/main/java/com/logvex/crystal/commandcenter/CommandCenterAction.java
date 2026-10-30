package com.logvex.crystal.commandcenter;

/**
 * One action the Command Center can perform, described so it can be discovered, validated
 * and invoked uniformly.
 *
 * <p>Today the command surface is a large switch of hand-written methods. The registry gives
 * each action a stable id, a label, a category and a description, so the GUI can list them,
 * the command surface can dispatch them by name, and the AI context can describe what the
 * client can do. An action is responsible for its own validation: {@link #available()} must
 * return false (with a reason in {@link #unavailableReason()}) when the action cannot run
 * right now, and {@link #run()} must never throw.
 */
public interface CommandCenterAction {

    /** Stable id, e.g. {@code "diagnostics.report"}. */
    String id();

    /** Human label for a button. */
    String label();

    /** Grouping key, e.g. {@code "diagnostics"}, {@code "modules"}, {@code "profiles"}. */
    String category();

    /** One line explaining what the action does. */
    String description();

    /** True when the action can run right now (its preconditions are met). */
    default boolean available() {
        return true;
    }

    /** Why the action is unavailable; empty when it is available. */
    default String unavailableReason() {
        return "";
    }

    /**
     * Runs the action and returns its outcome. Implementations must not throw; a failure is
     * an {@link ActionResult#failure} with a human-readable message.
     */
    ActionResult run();
}
