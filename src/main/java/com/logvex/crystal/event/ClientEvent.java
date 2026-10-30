package com.logvex.crystal.event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One observed state change on the bus.
 *
 * <p>Deliberately a plain, immutable value with no Minecraft types: the producer fills a small
 * string-keyed detail map, and a subscriber reads only what it needs. This keeps the bus
 * testable without a game and stops a subscriber from reaching back into live world state.
 *
 * <p>{@link #dedupKey()} identifies "the same transition" so the bus can suppress an event
 * storm when a producer re-reports an unchanged state.
 *
 * @param kind       what changed
 * @param atMillis   wall-clock time the event was observed
 * @param detail     small, non-sensitive key/value payload (names, ids, counts - never a token)
 */
public record ClientEvent(EventKind kind, long atMillis, Map<String, String> detail) {

    public ClientEvent {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    /** A builder for readable construction at a producer. */
    public static Builder of(EventKind kind, long atMillis) {
        return new Builder(kind, atMillis);
    }

    /** A detail value, or {@code fallback} when the key is absent. */
    public String get(String key, String fallback) {
        return detail.getOrDefault(key, fallback);
    }

    /** An integer detail value, or {@code fallback} when absent or unparseable. */
    public int getInt(String key, int fallback) {
        String value = detail.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * A stable key describing this transition. Two events with the same kind and the same key
     * describe the same state, so the bus records only the first within a debounce window.
     */
    public String dedupKey() {
        return kind + "|" + detail;
    }

    @Override
    public String toString() {
        return kind + " " + detail;
    }

    /** Fluent builder; detail insertion order is preserved so {@link #dedupKey} is stable. */
    public static final class Builder {
        private final EventKind kind;
        private final long atMillis;
        private final Map<String, String> detail = new LinkedHashMap<>();

        private Builder(EventKind kind, long atMillis) {
            this.kind = kind;
            this.atMillis = atMillis;
        }

        public Builder put(String key, String value) {
            if (key != null && value != null) {
                detail.put(key, value);
            }
            return this;
        }

        public Builder put(String key, int value) {
            return put(key, Integer.toString(value));
        }

        public Builder put(String key, long value) {
            return put(key, Long.toString(value));
        }

        public Builder put(String key, boolean value) {
            return put(key, Boolean.toString(value));
        }

        public Builder put(String key, double value) {
            return put(key, Double.toString(value));
        }

        public ClientEvent build() {
            return new ClientEvent(kind, atMillis, detail);
        }
    }
}
