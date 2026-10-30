package com.logvex.crystal.integration.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Transition and goal-completion tests for {@link BaritoneNavigationState}. */
class BaritoneNavigationStateTest {

    private static BaritoneStatus idle() {
        return new BaritoneStatus(true, "1.10", "", false, "none", "",
                BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD, BaritoneStatus.NO_COORD,
                false, false, false, BaritoneStatus.UNKNOWN, BaritoneStatus.UNKNOWN, false, false, "idle");
    }

    private static BaritoneStatus pathing(String detail) {
        return new BaritoneStatus(true, "1.10", "", true, "GoalBlock", detail,
                10, 64, -20, true, false, true, 40, 200, false, false, "block " + detail);
    }

    private static BaritoneStatus unavailable() {
        return BaritoneStatus.unavailable("not installed");
    }

    @Test
    void firstStatusIsInit() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        assertEquals(BaritoneNavigationState.Transition.INIT, state.update(idle(), 1).transition());
    }

    @Test
    void idleToGoalIsStarted() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(idle(), 1);
        assertEquals(BaritoneNavigationState.Transition.STARTED, state.update(pathing("1 2 3"), 2).transition());
    }

    @Test
    void goalToIdleIsCleared() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(pathing("1 2 3"), 1);
        assertEquals(BaritoneNavigationState.Transition.CLEARED, state.update(idle(), 2).transition());
    }

    @Test
    void goalDetailChangeIsChanged() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(pathing("1 2 3"), 1);
        assertEquals(BaritoneNavigationState.Transition.CHANGED, state.update(pathing("4 5 6"), 2).transition());
    }

    @Test
    void repeatedStatusIsNoTransition() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(pathing("1 2 3"), 1);
        assertEquals(BaritoneNavigationState.Transition.NONE, state.update(pathing("1 2 3"), 2).transition());
    }

    @Test
    void unavailableStatusIsReportedOnce() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        assertEquals(BaritoneNavigationState.Transition.UNAVAILABLE, state.update(unavailable(), 1).transition());
        assertEquals(BaritoneNavigationState.Transition.NONE, state.update(unavailable(), 2).transition());
    }

    @Test
    void issuedGoalCompletingFiresExactlyOnce() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(idle(), 1);
        state.markGoalIssued("block 1 2 3");
        state.update(pathing("1 2 3"), 2);
        BaritoneNavigationState.Outcome arrived = state.update(idle(), 3);
        assertTrue(arrived.goalReached(), "goal completion should be reported");
        assertFalse(state.update(idle(), 4).goalReached(), "completion must not repeat");
        assertFalse(state.hasIssuedGoal());
    }

    @Test
    void cancellationSuppressesCompletionNotice() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        state.update(idle(), 1);
        state.markGoalIssued("block 1 2 3");
        state.update(pathing("1 2 3"), 2);
        state.markGoalCancelled();
        assertFalse(state.update(idle(), 3).goalReached());
    }

    @Test
    void nullStatusIsIgnored() {
        BaritoneNavigationState state = new BaritoneNavigationState(8);
        assertEquals(BaritoneNavigationState.Transition.NONE, state.update(null, 1).transition());
    }
}
