package com.logvex.crystal.integration.baritone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A bounded history of navigation state transitions and integration errors.
 *
 * <p>Pure and Minecraft-free, so it is fully unit-testable. The service feeds it one
 * {@link BaritoneStatus} per poll; the model records an entry only when the status's
 * {@link BaritoneStatus#stateKey() state key} changes, so a long-running path does not fill
 * the buffer with duplicates. Capacity is fixed and old entries are evicted, so memory cannot
 * grow without bound during a long session.
 */
public final class BaritoneHistory {

    /** A single recorded transition or error. */
    public record Entry(long atMillis, String kind, String detail) {
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private String lastKey = "<none>";

    public BaritoneHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Records a state transition when {@code status}'s key differs from the last recorded one.
     *
     * @return true when an entry was added
     */
    public boolean observe(BaritoneStatus status, long nowMillis) {
        if (status == null) {
            return false;
        }
        String key = status.stateKey();
        if (key.equals(lastKey)) {
            return false;
        }
        String previous = lastKey;
        lastKey = key;
        add(new Entry(nowMillis, transitionKind(previous, key), describe(status)));
        return true;
    }

    /** Records an integration error, always (errors are not deduplicated by state). */
    public void error(String detail, long nowMillis) {
        add(new Entry(nowMillis, "error", detail == null ? "unknown error" : detail));
    }

    private void add(Entry entry) {
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    private static String transitionKind(String previous, String key) {
        if ("<none>".equals(previous)) {
            return "init";
        }
        if ("idle".equals(key)) {
            return "cleared";
        }
        if ("idle".equals(previous)) {
            return "started";
        }
        return "changed";
    }

    private static String describe(BaritoneStatus status) {
        if (!status.available()) {
            return "unavailable: " + status.reason();
        }
        if (!status.hasGoal()) {
            return "idle";
        }
        return status.goalType() + (status.goalDetail().isEmpty() ? "" : " " + status.goalDetail())
                + (status.pathing() ? " (pathing)" : "");
    }

    /** Most recent entries first, up to {@code limit}. */
    public List<Entry> recent(int limit) {
        int count = Math.min(limit, entries.size());
        List<Entry> result = new ArrayList<>(count);
        java.util.Iterator<Entry> it = entries.descendingIterator();
        while (it.hasNext() && result.size() < count) {
            result.add(it.next());
        }
        return result;
    }

    public int size() {
        return entries.size();
    }

    public int capacity() {
        return capacity;
    }

    /** The last observed state key, or {@code "<none>"} before the first observation. */
    public String lastKey() {
        return lastKey;
    }

    public void clear() {
        entries.clear();
        lastKey = "<none>";
    }
}
