package com.logvex.crystal.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, bounded record of module failures for the {@code ; errors} diagnostic.
 *
 * <p>Module errors are already written to the log with a stack trace; this keeps the most
 * recent ones in memory so the player can see, in-game, which module misbehaved without
 * digging through a log file. The ring buffer is bounded, counts are kept per module, and
 * the whole thing is Minecraft-independent so it can be unit tested. Producers run on the
 * client thread and the command reads on the same thread, but the methods are synchronized
 * so a background producer (e.g. a scan worker) cannot corrupt it.
 */
public final class ModuleErrorLog {

    /** One captured failure. */
    public record Entry(String module, String message, long atMillis) {
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public ModuleErrorLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized void record(String module, Throwable error) {
        record(module, describe(error));
    }

    public synchronized void record(String module, String message) {
        String name = module == null ? "unknown" : module;
        String text = message == null ? "" : message;
        entries.addLast(new Entry(name, text, System.currentTimeMillis()));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        counts.merge(name, 1, Integer::sum);
    }

    /** Most-recent-last snapshot of the retained failures. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** The most recent failure, or null when nothing has failed. */
    public synchronized Entry latest() {
        return entries.peekLast();
    }

    /** Per-module failure counts, highest first. */
    public synchronized List<Map.Entry<String, Integer>> ranked() {
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return ranked;
    }

    public synchronized int totalRecorded() {
        int total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        return total;
    }

    public synchronized int distinctModules() {
        return counts.size();
    }

    public synchronized void clear() {
        entries.clear();
        counts.clear();
    }

    /** A one-line summary of a throwable, preserving the original as the message. */
    public static String describe(Throwable error) {
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }
}
