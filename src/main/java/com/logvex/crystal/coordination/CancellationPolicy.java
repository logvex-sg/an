package com.logvex.crystal.coordination;

/**
 * What the {@link ActionCoordinator} does when an action's precondition stops holding or its
 * timeout elapses. Modelled explicitly so a module's intent is part of its declaration rather
 * than an accident of how it wrote its tick loop.
 */
public enum CancellationPolicy {

    /**
     * Abandon the action as soon as the precondition fails. The safe default for actions that
     * are only valid while their precondition holds (e.g. "place a crystal" only while the
     * spot is still free and the target still exists).
     */
    ON_PRECONDITION,

    /**
     * Keep the action pending until its timeout even if the precondition flickers, then
     * abandon. Use for actions whose precondition is momentarily unstable (e.g. a one-tick
     * inventory lag) but which should not be restarted from scratch.
     */
    ON_TIMEOUT,

    /**
     * Never cancel automatically; the owner must cancel explicitly. Reserved for actions whose
     * completion is the only valid stop condition and which must not be interrupted.
     */
    NEVER
}
