package com.logvex.crystal.integration.baritone;

import java.util.List;

/**
 * The narrow seam between LOGVEX CRYSTAL and Baritone.
 *
 * <p>Every Baritone call the client makes goes through this interface, so the rest of the
 * codebase never references a Baritone type directly. There are two implementations:
 *
 * <ul>
 *   <li>{@link ReflectiveBaritoneBridge} - the production bridge. Baritone is not a compile
 *       dependency, so it reaches the API through validated reflection, isolated here.</li>
 *   <li>a test double in the unit tests, so navigation-state handling is exercised without a
 *       live game or a Baritone jar.</li>
 * </ul>
 *
 * <p>Implementations must be safe to call from the client thread only. Every method returns a
 * definite result (or throws a checked {@link BaritoneException}) and never leaves the client
 * in a broken state.
 */
public interface BaritoneBridge {

    /** The result of the initial availability probe. */
    record Availability(boolean available, String version, String reason) {
        public static Availability ok(String version) {
            return new Availability(true, version == null ? "unknown" : version, "");
        }

        public static Availability no(String reason) {
            return new Availability(false, "unknown", reason == null ? "" : reason);
        }
    }

    /** Thrown when a Baritone call fails; carries a user-facing message. */
    class BaritoneException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BaritoneException(String message) {
            super(message);
        }

        public BaritoneException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Probes for Baritone once and caches the result. Never throws. */
    Availability probe();

    /** Reads the current navigation state. Never throws; returns an unavailable status on failure. */
    BaritoneStatus status();

    /**
     * Sets a block goal and starts pathing.
     *
     * @throws BaritoneException when Baritone is unavailable or rejects the request
     */
    void gotoBlock(int x, int y, int z);

    /**
     * Sets a goal and starts pathing.
     *
     * @param kind one of {@link BaritoneGoal.Kind}
     * @param x    x coordinate, or 0 when unused by the goal kind
     * @param y    y coordinate, or 0 when unused by the goal kind
     * @param z    z coordinate, or 0 when unused by the goal kind
     * @throws BaritoneException when Baritone is unavailable or rejects the request
     */
    void gotoGoal(BaritoneGoal.Kind kind, int x, int y, int z);

    /**
     * Sets a goal, or a goal with a follow radius for {@code GOAL_NEAR}.
     *
     * @throws BaritoneException when Baritone is unavailable or rejects the request
     */
    void follow(BaritoneGoal.Kind kind, int x, int y, int z, int range);

    /** Cancels the current goal/path. Never throws. */
    void cancel();

    /**
     * Requests a pause of whatever Baritone is currently doing.
     *
     * <p>Baritone's pause is a <em>registered process</em> that returns
     * {@code PathingCommandType.REQUEST_PAUSE} while a flag is set; there is no public method on
     * {@code IPathingBehavior} to trigger it (the concrete {@code requestPause()} is not exposed
     * on the API interface). The supported way to pause - the same one Baritone's own
     * {@code #pause} command uses - is to run that command through Baritone's command manager.
     * This is deliberately <b>not</b> a cancel: a pause is resumable, a cancel is not.
     *
     * @return true when the pause request was issued
     * @throws BaritoneException when Baritone is unavailable
     */
    boolean pause();

    /**
     * Resumes Baritone after a {@link #pause()}, via Baritone's own {@code #resume} command.
     *
     * @return true when the resume request was issued
     * @throws BaritoneException when Baritone is unavailable
     */
    boolean resume();

    /**
     * Writes one Baritone setting through its public {@code Setting} API.
     *
     * @return true when the value was written
     * @throws BaritoneException when the setting does not exist or has the wrong type
     */
    boolean setSetting(String name, String value);

    /** Reads one Baritone setting as text, or null when it is not exposed. */
    String getSetting(String name);

    /** The names of every writable Baritone setting, or an empty list when unavailable. */
    List<String> settingNames();

    /**
     * The client-side command that would accomplish {@code action}, for the "show command"
     * diagnostic. Returns null when the action has no command equivalent.
     */
    String commandFor(String action);
}
