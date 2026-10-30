package com.logvex.crystal.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers action arbitration: submission, dedup, completion, cancellation and timeouts. */
class ActionCoordinatorTest {

    /** A controllable action for tests. */
    private static final class FakeAction implements ClientAction {
        private final String owner;
        private final String kind;
        private final int priority;
        private final String group;
        private final long timeout;
        private final CancellationPolicy policy;
        private boolean precond = true;
        private boolean complete;
        private boolean throwOnRun;
        private final AtomicInteger runs = new AtomicInteger();

        FakeAction(String owner, String kind) {
            this(owner, kind, 0, "", 1_000L, CancellationPolicy.ON_PRECONDITION);
        }

        FakeAction(String owner, String kind, int priority, String group, long timeout,
                   CancellationPolicy policy) {
            this.owner = owner;
            this.kind = kind;
            this.priority = priority;
            this.group = group;
            this.timeout = timeout;
            this.policy = policy;
        }

        void setPrecondition(boolean value) {
            this.precond = value;
        }

        void setComplete(boolean value) {
            this.complete = value;
        }

        void setThrowOnRun(boolean value) {
            this.throwOnRun = value;
        }

        int runs() {
            return runs.get();
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public String owner() {
            return owner;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public String exclusiveGroup() {
            return group;
        }

        @Override
        public boolean precondition() {
            return precond;
        }

        @Override
        public CancellationPolicy cancellation() {
            return policy;
        }

        @Override
        public long timeoutMillis() {
            return timeout;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void run() {
            runs.incrementAndGet();
            if (throwOnRun) {
                throw new IllegalStateException("boom");
            }
        }
    }

    @Test
    void acceptedActionRunsAndCompletesOnlyWhenVerified() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("AutoCrystal", "place");
        assertEquals(ActionCoordinator.SubmitResult.ACCEPTED, coordinator.submit(action, 0));

        coordinator.tick(0);
        assertEquals(1, action.runs(), "run() executes once");
        assertEquals(ActionState.RUNNING, coordinator.active().get(0).state(),
                "not complete just because run() was called");

        action.setComplete(true);
        coordinator.tick(10);
        assertEquals(0, coordinator.activeCount());
        assertEquals(1, coordinator.completedTotal());
    }

    @Test
    void duplicateSubmissionIsMergedNotQueued() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction first = new FakeAction("AutoCrystal", "place");
        FakeAction second = new FakeAction("AutoCrystal", "place");
        assertEquals(ActionCoordinator.SubmitResult.ACCEPTED, coordinator.submit(first, 0));
        assertEquals(ActionCoordinator.SubmitResult.MERGED, coordinator.submit(second, 1));
        assertEquals(1, coordinator.activeCount());
        assertEquals(1, coordinator.mergedTotal());
    }

    @Test
    void actionWithFailingPreconditionIsRejectedAtSubmit() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("Surround", "place");
        action.setPrecondition(false);
        assertEquals(ActionCoordinator.SubmitResult.REJECTED, coordinator.submit(action, 0));
        assertEquals(0, coordinator.activeCount());
    }

    @Test
    void preconditionFailureCancelsARunningAction() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("Surround", "place");
        coordinator.submit(action, 0);
        coordinator.tick(0);
        assertEquals(ActionState.RUNNING, coordinator.active().get(0).state());

        action.setPrecondition(false);
        coordinator.tick(10);
        assertEquals(0, coordinator.activeCount());
        assertEquals(1, coordinator.cancelledTotal());
    }

    @Test
    void timeoutAbandonsAStuckAction() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("AutoCrystal", "place", 0, "", 500L,
                CancellationPolicy.ON_TIMEOUT);
        coordinator.submit(action, 0);
        coordinator.tick(0);
        coordinator.tick(600);
        assertEquals(0, coordinator.activeCount());
        assertEquals(1, coordinator.timedOutTotal());
    }

    @Test
    void throwingRunIsRecordedAsFailedAndDoesNotPropagate() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("AutoCrystal", "place");
        action.setThrowOnRun(true);
        coordinator.submit(action, 0);
        coordinator.tick(0);
        assertEquals(1, coordinator.failedTotal());
        assertEquals(0, coordinator.activeCount());
    }

    @Test
    void higherPriorityOwnerWinsTheExclusiveGroup() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction low = new FakeAction("Cosmetic", "swing", 0, "main-hand", 1_000L,
                CancellationPolicy.NEVER);
        FakeAction high = new FakeAction("AutoCrystal", "attack", 50, "main-hand", 1_000L,
                CancellationPolicy.NEVER);
        coordinator.submit(low, 0);
        coordinator.submit(high, 0);
        coordinator.tick(0);

        assertEquals(0, low.runs(), "the lower-priority owner must not run");
        assertEquals(1, high.runs(), "the highest-priority owner runs first");
    }

    @Test
    void cancelOwnerStopsOnlyThatOwnersActions() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction a = new FakeAction("AutoCrystal", "place");
        FakeAction b = new FakeAction("Surround", "place");
        coordinator.submit(a, 0);
        coordinator.submit(b, 0);
        assertEquals(1, coordinator.cancelOwner("AutoCrystal", 5));
        assertEquals(1, coordinator.activeCount());
        assertEquals("Surround", coordinator.active().get(0).owner());
    }

    @Test
    void completedVerificationBeatsAPendingGroup() {
        ActionCoordinator coordinator = new ActionCoordinator();
        FakeAction action = new FakeAction("AutoCrystal", "attack", 50, "main-hand", 1_000L,
                CancellationPolicy.NEVER);
        action.setComplete(true);
        coordinator.submit(action, 0);
        coordinator.tick(0);
        assertEquals(1, coordinator.completedTotal());
        assertEquals(0, action.runs(), "an already-satisfied action need not run");
    }
}
