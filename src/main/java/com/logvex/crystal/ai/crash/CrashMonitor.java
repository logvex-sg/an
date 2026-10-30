package com.logvex.crystal.ai.crash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-memory crash registry: deduplicates by fingerprint, keeps a bounded history, and
 * notifies listeners when a genuinely new crash arrives. Minecraft-independent so it is unit
 * tested directly.
 *
 * <p>Recording is deliberately cheap — a map insert and a bounded deque append — so it is safe
 * to call from an uncaught-exception handler. Anything expensive (analysis, disk, network)
 * happens later, off the crash path.
 */
public final class CrashMonitor {

    /** Notified when a new (unseen) crash is recorded. */
    public interface Listener {
        void onCrash(CrashRecord record);
    }

    private final int maxHistory;
    private final List<CrashRecord> history = new ArrayList<>();
    private final Map<String, Integer> seenCounts = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public CrashMonitor(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Records a crash. Returns true when it is a new fingerprint, false when it is a repeat
     * (the repeat still bumps the seen count, for flood control and "seen N times").
     */
    public synchronized boolean record(CrashRecord record) {
        if (record == null) {
            return false;
        }
        String fp = record.fingerprint();
        boolean isNew = !seenCounts.containsKey(fp);
        seenCounts.merge(fp, 1, Integer::sum);
        if (isNew) {
            history.add(record);
            while (history.size() > maxHistory) {
                history.remove(0);
            }
            for (Listener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onCrash(record);
                } catch (RuntimeException ignored) {
                    // A broken listener must not break crash capture.
                }
            }
        }
        return isNew;
    }

    public synchronized boolean hasSeen(String fingerprint) {
        return fingerprint != null && seenCounts.containsKey(fingerprint);
    }

    public synchronized int seenCount(String fingerprint) {
        return fingerprint == null ? 0 : seenCounts.getOrDefault(fingerprint, 0);
    }

    public synchronized List<CrashRecord> history() {
        return new ArrayList<>(history);
    }

    public synchronized CrashRecord latest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public synchronized int size() {
        return history.size();
    }

    public synchronized int distinctFingerprints() {
        return seenCounts.size();
    }

    /** Removes one record by id; returns true when something was removed. */
    public synchronized boolean remove(String id) {
        return history.removeIf(record -> record.id().equals(id));
    }

    /** Clears history and seen counts (used by "delete stored reports"). */
    public synchronized void clear() {
        history.clear();
        seenCounts.clear();
    }
}
