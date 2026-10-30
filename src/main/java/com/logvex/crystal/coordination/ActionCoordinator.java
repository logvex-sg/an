package com.logvex.crystal.coordination;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shared arbitration layer for client actions.
 *
 * <p>Before this existed, every acting module drove the interaction manager on its own tick
 * with no knowledge of the others, so two modules could place, break or attack in the same
 * tick. The coordinator makes the decision explicit: a module submits a {@link ClientAction},
 * and the coordinator decides whether and when it runs, enforces mutual exclusion between
 * actions that share an {@link ClientAction#exclusiveGroup()}, re-checks preconditions,
 * applies timeouts, verifies completion and keeps a bounded record of outcomes.
 *
 * <p>It never assumes an action succeeded: an action is only {@link ActionState#COMPLETED}
 * when its own {@link ClientAction#isComplete()} verification passes.
 *
 * <p>Threading: every method runs on the client thread. Methods are synchronized only so the
 * command/GUI layer can read {@link #history()} safely during a frame; there are no worker
 * threads here.
 *
 * <p>Minecraft-independent by design: the clock is passed in, so the whole policy is unit
 * testable without a game.
 */
public final class ActionCoordinator {

    /** The outcome of submitting an action. */
    public enum SubmitResult {
        /** A new action was accepted. */
        ACCEPTED,
        /** An identical action (same owner and kind) is already active; the request was merged. */
        MERGED,
        /** The action was rejected outright (null, or its precondition already fails). */
        REJECTED
    }

    /** One submitted action and its live state. */
    public static final class Entry {
        private final ClientAction action;
        private final long sequence;
        private final long acceptedAt;
        private final String groupKey;
        private ActionState state = ActionState.PENDING;
        private boolean ran;
        private long finishedAt;
        private String detail = "";

        private Entry(ClientAction action, long sequence, long acceptedAt) {
            this.action = action;
            this.sequence = sequence;
            this.acceptedAt = acceptedAt;
            this.groupKey = action.exclusiveGroup() == null ? ""
                    : action.exclusiveGroup().trim().toLowerCase(Locale.ROOT);
        }

        public ClientAction action() {
            return action;
        }

        public ActionState state() {
            return state;
        }

        public long acceptedAt() {
            return acceptedAt;
        }

        public long finishedAt() {
            return finishedAt;
        }

        public String detail() {
            return detail;
        }

        public String owner() {
            return action.owner();
        }

        public String kind() {
            return action.kind();
        }

        private boolean isDuplicateOf(ClientAction other) {
            return action.owner().equalsIgnoreCase(other.owner())
                    && action.kind().equalsIgnoreCase(other.kind());
        }
    }

    private static final int HISTORY_CAPACITY = 128;

    private final List<Entry> active = new ArrayList<>();
    private final Deque<Entry> history = new ArrayDeque<>();
    private long sequence;

    private int submittedTotal;
    private int completedTotal;
    private int cancelledTotal;
    private int timedOutTotal;
    private int failedTotal;
    private int mergedTotal;

    /**
     * Submits an action.
     *
     * @return {@link SubmitResult#ACCEPTED} for a new action, {@link SubmitResult#MERGED} when an
     *         identical action by the same owner is already active (so repeated per-tick requests
     *         do not pile up), or {@link SubmitResult#REJECTED} for a null action or one whose
     *         precondition already fails.
     */
    public synchronized SubmitResult submit(ClientAction action, long nowMillis) {
        if (action == null) {
            return SubmitResult.REJECTED;
        }
        for (Entry entry : active) {
            if (entry.state.active() && entry.isDuplicateOf(action)) {
                mergedTotal++;
                return SubmitResult.MERGED;
            }
        }
        if (!action.precondition()) {
            return SubmitResult.REJECTED;
        }
        Entry entry = new Entry(action, sequence++, nowMillis);
        active.add(entry);
        submittedTotal++;
        return SubmitResult.ACCEPTED;
    }

    /**
     * Advances every active action one step. Call once per client tick.
     *
     * <p>Processing order is priority (high first) then submission order, so a higher-priority
     * owner in an exclusive group wins deterministically.
     */
    public synchronized void tick(long nowMillis) {
        if (active.isEmpty()) {
            return;
        }
        active.sort((a, b) -> {
            int byPriority = Integer.compare(b.action.priority(), a.action.priority());
            return byPriority != 0 ? byPriority : Long.compare(a.sequence, b.sequence);
        });
        List<Entry> finished = new ArrayList<>();
        for (Entry entry : active) {
            step(entry, nowMillis);
            if (!entry.state.active()) {
                entry.finishedAt = nowMillis;
                finished.add(entry);
            }
        }
        for (Entry entry : finished) {
            active.remove(entry);
            record(entry);
        }
    }

    private void step(Entry entry, long nowMillis) {
        ClientAction action = entry.action;

        // Verified completion wins over everything, even before the first run().
        if (action.isComplete()) {
            entry.state = ActionState.COMPLETED;
            entry.detail = "verified complete";
            completedTotal++;
            return;
        }

        long elapsed = nowMillis - entry.acceptedAt;
        long timeout = action.timeoutMillis();
        if (timeout > 0 && elapsed >= timeout) {
            entry.state = ActionState.TIMEOUT;
            entry.detail = "timed out after " + elapsed + "ms";
            timedOutTotal++;
            return;
        }

        CancellationPolicy policy = action.cancellation();
        if (policy == CancellationPolicy.ON_PRECONDITION && !action.precondition()) {
            entry.state = ActionState.CANCELLED;
            entry.detail = "precondition failed";
            cancelledTotal++;
            return;
        }
        if (policy == CancellationPolicy.ON_TIMEOUT && !action.precondition() && elapsed >= timeout) {
            entry.state = ActionState.CANCELLED;
            entry.detail = "precondition failed at timeout";
            cancelledTotal++;
            return;
        }

        if (entry.state == ActionState.PENDING) {
            if (!groupFree(entry)) {
                return; // A higher-priority owner in the group is running; wait (still subject to timeout).
            }
            entry.state = ActionState.RUNNING;
        }
        if (!entry.ran) {
            entry.ran = true;
            try {
                action.run();
            } catch (Throwable t) {
                entry.state = ActionState.FAILED;
                entry.detail = t.getClass().getSimpleName() + ": " + t.getMessage();
                failedTotal++;
            }
        }
    }

    /** True when no other running action shares {@code entry}'s non-empty exclusive group. */
    private boolean groupFree(Entry entry) {
        if (entry.groupKey.isEmpty()) {
            return true;
        }
        for (Entry other : active) {
            if (other == entry || other.state != ActionState.RUNNING) {
                continue;
            }
            if (other.groupKey.equals(entry.groupKey)) {
                return false;
            }
        }
        return true;
    }

    /** Cancels every active action owned by {@code owner}. Returns how many were cancelled. */
    public synchronized int cancelOwner(String owner, long nowMillis) {
        if (owner == null) {
            return 0;
        }
        int cancelled = 0;
        List<Entry> finished = new ArrayList<>();
        for (Entry entry : active) {
            if (entry.state.active() && entry.owner().equalsIgnoreCase(owner)) {
                entry.state = ActionState.CANCELLED;
                entry.detail = "cancelled by owner";
                entry.finishedAt = nowMillis;
                cancelledTotal++;
                cancelled++;
                finished.add(entry);
            }
        }
        for (Entry entry : finished) {
            active.remove(entry);
            record(entry);
        }
        return cancelled;
    }

    /** Cancels every active action. */
    public synchronized void cancelAll(long nowMillis) {
        List<Entry> finished = new ArrayList<>(active);
        for (Entry entry : finished) {
            if (entry.state.active()) {
                entry.state = ActionState.CANCELLED;
                entry.detail = "cancelled";
                entry.finishedAt = nowMillis;
                cancelledTotal++;
            }
        }
        active.clear();
        for (Entry entry : finished) {
            record(entry);
        }
    }

    /** True when an action owned by {@code owner} with {@code kind} is active. */
    public synchronized boolean isActive(String owner, String kind) {
        for (Entry entry : active) {
            if (entry.state.active() && entry.owner().equalsIgnoreCase(owner)
                    && entry.kind().equalsIgnoreCase(kind)) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<Entry> active() {
        return new ArrayList<>(active);
    }

    /** Recent finished actions, oldest first, bounded by the history capacity. */
    public synchronized List<Entry> history() {
        return new ArrayList<>(history);
    }

    private void record(Entry entry) {
        history.addLast(entry);
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized int submittedTotal() {
        return submittedTotal;
    }

    public synchronized int completedTotal() {
        return completedTotal;
    }

    public synchronized int cancelledTotal() {
        return cancelledTotal;
    }

    public synchronized int timedOutTotal() {
        return timedOutTotal;
    }

    public synchronized int failedTotal() {
        return failedTotal;
    }

    public synchronized int mergedTotal() {
        return mergedTotal;
    }

    /** A one-line summary for the diagnostics view. */
    public synchronized String summary() {
        return "actions: " + active.size() + " active, " + completedTotal + " done, "
                + cancelledTotal + " cancelled, " + timedOutTotal + " timed out, "
                + failedTotal + " failed, " + mergedTotal + " merged";
    }

    /** Group key -&gt; the kind of the highest-priority action currently occupying it. */
    public synchronized Map<String, String> occupiedGroups() {
        Map<String, String> occupied = new LinkedHashMap<>();
        for (Entry entry : active) {
            if (entry.state == ActionState.RUNNING && !entry.groupKey.isEmpty()) {
                occupied.putIfAbsent(entry.groupKey, entry.kind());
            }
        }
        return occupied;
    }
}
