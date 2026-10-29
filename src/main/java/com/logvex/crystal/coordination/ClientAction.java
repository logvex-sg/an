package com.logvex.crystal.coordination;

/**
 * A unit of work a module wants the shared {@link ActionCoordinator} to run.
 *
 * <p>The coordinator does not itself know how to place a block or swing a hand. It owns the
 * <em>policy</em> - who may act, in what order, with what preconditions, how long before it is
 * abandoned, and how completion is verified. The module supplies the concrete execution via
 * {@link #run()} and the completion test via {@link #isComplete()}.
 *
 * <p>Contract for an implementation:
 * <ul>
 *   <li>{@link #precondition()} is re-checked every tick; when it turns false the action is
 *       cancelled (see {@link CancellationPolicy}).</li>
 *   <li>{@link #run()} is called at most once per accepted request. It must not block.</li>
 *   <li>{@link #isComplete()} is the verification step: an action is only reported complete
 *       when the observed world actually matches the expected result, never merely because a
 *       request was issued.</li>
 * </ul>
 */
public interface ClientAction {

    /** Stable identifier of the action kind (e.g. {@code "place-obsidian"}), for diagnostics. */
    String kind();

    /** The module that owns this action, used for arbitration and reporting. */
    String owner();

    /**
     * Higher priority wins when two actions of the same {@link #exclusiveGroup()} are queued.
     * A sensible convention: movement 10, combat 50, inventory 30, cosmetic 0.
     */
    default int priority() {
        return 0;
    }

    /**
     * Actions sharing a non-empty group are mutually exclusive within the coordinator: only
     * the highest-priority owner in the group runs at a time. An empty string means "no group",
     * so the action never blocks another.
     */
    default String exclusiveGroup() {
        return "";
    }

    /** True when the action may currently start or continue. Re-checked every tick. */
    default boolean precondition() {
        return true;
    }

    /** What to do when the precondition fails while the action is pending or running. */
    default CancellationPolicy cancellation() {
        return CancellationPolicy.ON_PRECONDITION;
    }

    /** How long the action may run before it is abandoned, in milliseconds. 0 means no limit. */
    default long timeoutMillis() {
        return 2_000L;
    }

    /** True when the action has achieved its verified result. */
    boolean isComplete();

    /** Performs one step of the action. Must not block; called on the client thread. */
    void run();

    /** A short human description for the diagnostics view. */
    default String describe() {
        return kind() + " (" + owner() + ")";
    }
}
