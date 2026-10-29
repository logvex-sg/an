package com.logvex.crystal.integration.baritone;

/**
 * The Minecraft-free navigation state machine behind {@link BaritoneService}.
 *
 * <p>It holds the latest {@link BaritoneStatus}, records transitions in a bounded
 * {@link BaritoneHistory}, and decides when a goal the client issued has completed. Keeping
 * this logic free of Minecraft types is what lets it be unit-tested against a fake bridge; the
 * service adds polling, notifications and the client-thread plumbing on top.
 */
public final class BaritoneNavigationState {

    /** What changed on the last {@link #update}. */
    public enum Transition {
        /** No change since the previous status. */
        NONE,
        /** First status observed. */
        INIT,
        /** A goal appeared where there was none. */
        STARTED,
        /** A goal disappeared. */
        CLEARED,
        /** The goal or pathing state changed while a goal was set. */
        CHANGED,
        /** Baritone became unavailable (or remained so). */
        UNAVAILABLE
    }

    /** The result of an update: the transition, and whether an issued goal just completed. */
    public record Outcome(Transition transition, boolean goalReached) {
    }

    private final BaritoneHistory history;
    private BaritoneStatus status = BaritoneStatus.unavailable("not probed yet");
    private boolean first = true;
    private boolean goalIssued;
    private String issuedGoal = "";

    public BaritoneNavigationState(int historyCapacity) {
        this.history = new BaritoneHistory(historyCapacity);
    }

    /**
     * Records a new status snapshot.
     *
     * @param next the freshly read status; null is ignored
     * @param now  wall-clock millis for history timestamps
     * @return the transition and whether a previously issued goal just completed
     */
    public Outcome update(BaritoneStatus next, long now) {
        if (next == null) {
            return new Outcome(Transition.NONE, false);
        }
        boolean previousHadGoal = status.available() && status.hasGoal();
        boolean changed = history.observe(next, now);
        status = next;

        boolean reached = false;
        if (goalIssued && !next.hasGoal()) {
            goalIssued = false;
            issuedGoal = "";
            reached = true;
        }

        if (!next.available()) {
            boolean announce = changed || first;
            first = false;
            return new Outcome(announce ? Transition.UNAVAILABLE : Transition.NONE, reached);
        }
        if (!changed) {
            first = false;
            return new Outcome(Transition.NONE, reached);
        }
        Transition transition;
        if (first) {
            transition = Transition.INIT;
        } else if (!next.hasGoal()) {
            transition = Transition.CLEARED;
        } else if (!previousHadGoal) {
            transition = Transition.STARTED;
        } else {
            transition = Transition.CHANGED;
        }
        first = false;
        return new Outcome(transition, reached);
    }

    /** Records that the client just issued a goal, so completion can be detected later. */
    public void markGoalIssued(String description) {
        this.goalIssued = true;
        this.issuedGoal = description == null ? "" : description;
    }

    /** Records that the client cancelled; clears the pending-completion expectation. */
    public void markGoalCancelled() {
        this.goalIssued = false;
        this.issuedGoal = "";
    }

    public BaritoneStatus status() {
        return status;
    }

    public BaritoneHistory history() {
        return history;
    }

    public boolean hasIssuedGoal() {
        return goalIssued;
    }

    public String issuedGoal() {
        return issuedGoal;
    }
}
