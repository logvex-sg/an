package com.logvex.crystal.ai.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A small, bounded ring of recent client diagnostic events plus an environment snapshot, used
 * to give a crash report context without scanning any log file.
 *
 * <p>Events are short strings ("module AutoCrystal enabled", "baritone goal set") pushed by the
 * client's own event feed at a debounced rate. The ring is bounded, so memory use is constant and
 * a crash can attach "the last N things that happened" cheaply. No file is read here.
 */
public final class DiagnosticContext {

    /** A point-in-time environment snapshot, all strings so it serialises without Minecraft. */
    public record Snapshot(
            String modVersion,
            String minecraftVersion,
            String loaderVersion,
            String javaVersion,
            String osName,
            String baritoneStatus,
            String aiStatus) {
    }

    private final int capacity;
    private final Deque<String> events = new ArrayDeque<>();
    private Snapshot snapshot;

    public DiagnosticContext(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.snapshot = new Snapshot("unknown", "unknown", "unknown", System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"), "unknown", "unknown");
    }

    /** Replaces the environment snapshot (called after services are constructed). */
    public synchronized void setSnapshot(Snapshot snapshot) {
        if (snapshot != null) {
            this.snapshot = snapshot;
        }
    }

    public synchronized Snapshot snapshot() {
        return snapshot;
    }

    /** Appends a short event, dropping the oldest when full. Ignores blanks. */
    public synchronized void record(String event) {
        if (event == null || event.isBlank()) {
            return;
        }
        events.addLast(event);
        while (events.size() > capacity) {
            events.removeFirst();
        }
    }

    /** A snapshot of the recent events, oldest first. */
    public synchronized List<String> recent() {
        return new ArrayList<>(events);
    }

    /** The most recent {@code n} events, oldest first. */
    public synchronized List<String> recent(int n) {
        List<String> all = new ArrayList<>(events);
        if (n >= all.size()) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - n, all.size()));
    }

    public synchronized void clear() {
        events.clear();
    }
}
