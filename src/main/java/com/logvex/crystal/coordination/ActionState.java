package com.logvex.crystal.coordination;

/**
 * The lifecycle state of a {@link ClientAction} inside the {@link ActionCoordinator}.
 *
 * <p>An action is only {@link #COMPLETED} when its own {@link ClientAction#isComplete()}
 * verification passes - never merely because it was requested or its {@code run()} step
 * executed.
 */
public enum ActionState {
    /** Accepted but not yet run (e.g. waiting for its exclusive group). */
    PENDING,
    /** Currently executing; {@code run()} has been called and it has not completed. */
    RUNNING,
    /** Verified complete. */
    COMPLETED,
    /** Abandoned because its precondition failed. */
    CANCELLED,
    /** Abandoned because it exceeded its timeout. */
    TIMEOUT,
    /** Abandoned because {@code run()} threw. */
    FAILED;

    /** True for a state that is still occupying the coordinator. */
    public boolean active() {
        return this == PENDING || this == RUNNING;
    }
}
