package com.logvex.crystal.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small, bounded, deduplicating event bus over observable client-side state changes.
 *
 * <p>Before this existed, {@code EntityTracker} produced spawn/despawn events that only a
 * polling caller could read, and other state changes (target switched, dimension changed,
 * Baritone state changed) had no unified notification at all. The bus gives every consumer one
 * place to subscribe, and one place to apply the anti-storm rules the mission calls out.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Bounded</b> - a fixed-size ring of recent events for late readers; nothing grows
 *       without limit.</li>
 *   <li><b>Deduplicated</b> - an event identical to the previous one of the same kind within
 *       the debounce window is dropped, so a producer that re-reports an unchanged state each
 *       tick cannot create a storm.</li>
 *   <li><b>Isolated</b> - a throwing listener is caught and never breaks dispatch or the
 *       tick loop.</li>
 *   <li><b>Minecraft-independent</b> - the clock is injected, so the whole policy is unit
 *       testable.</li>
 * </ul>
 *
 * <p>Threading: all methods run on the client thread.
 */
public final class ClientEventBus {

    /** A subscription handle; call {@link #close()} to stop receiving events. */
    public interface Subscription extends AutoCloseable {
        @Override
        void close();

        /** True while this subscription still receives events. */
        boolean active();
    }

    /** Per-listener counters, so a slow or noisy subscriber is visible in diagnostics. */
    public static final class Stats {
        private long delivered;
        private long dropped;

        public long delivered() {
            return delivered;
        }

        public long dropped() {
            return dropped;
        }
    }

    private static final int HISTORY_CAPACITY = 256;

    private final long dedupWindowMillis;
    private final Deque<ClientEvent> history = new ArrayDeque<>();
    private final List<Registration> registrations = new ArrayList<>();
    private final Map<EventKind, Long> lastSeen = new LinkedHashMap<>();

    private long publishedTotal;
    private long suppressedTotal;

    /**
     * @param dedupWindowMillis how long an identical event is suppressed; 0 disables
     *                          deduplication
     */
    public ClientEventBus(long dedupWindowMillis) {
        this.dedupWindowMillis = Math.max(0, dedupWindowMillis);
    }

    private static final class Registration {
        private final Set<EventKind> kinds;
        private final ClientEventListener listener;
        private final Stats stats = new Stats();
        private boolean closed;

        private Registration(Set<EventKind> kinds, ClientEventListener listener) {
            this.kinds = kinds;
            this.listener = listener;
        }

        private boolean matches(ClientEvent event) {
            return kinds == null || kinds.contains(event.kind());
        }
    }

    /** Subscribes to every event kind. */
    public Subscription subscribe(ClientEventListener listener) {
        return register(null, listener);
    }

    /** Subscribes to the given kinds only. */
    public Subscription subscribe(Set<EventKind> kinds, ClientEventListener listener) {
        return register(kinds == null || kinds.isEmpty() ? null : EnumSet.copyOf(kinds), listener);
    }

    private Subscription register(Set<EventKind> kinds, ClientEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        Registration registration = new Registration(kinds, listener);
        registrations.add(registration);
        return new Subscription() {
            @Override
            public void close() {
                registration.closed = true;
                registrations.remove(registration);
            }

            @Override
            public boolean active() {
                return !registration.closed;
            }
        };
    }

    /**
     * Publishes an event to every matching subscriber.
     *
     * @return true when the event was dispatched, false when it was suppressed as a duplicate
     *         of the previous event of the same kind
     */
    public boolean publish(ClientEvent event) {
        if (event == null) {
            return false;
        }
        if (isDuplicate(event)) {
            suppressedTotal++;
            return false;
        }
        publishedTotal++;
        history.addLast(event);
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
        // Iterate a copy so a listener may subscribe/unsubscribe during dispatch.
        for (Registration registration : new ArrayList<>(registrations)) {
            if (registration.closed || !registration.matches(event)) {
                continue;
            }
            try {
                registration.listener.onEvent(event);
                registration.stats.delivered++;
            } catch (Throwable t) {
                registration.stats.dropped++;
                // A broken listener must never break dispatch or the caller's tick.
            }
        }
        return true;
    }

    private boolean isDuplicate(ClientEvent event) {
        if (dedupWindowMillis <= 0) {
            return false;
        }
        String key = event.dedupKey();
        String lastKey = lastKeys.get(event.kind());
        Long lastAt = lastSeen.get(event.kind());
        if (key.equals(lastKey) && lastAt != null && event.atMillis() - lastAt < dedupWindowMillis) {
            return true;
        }
        lastKeys.put(event.kind(), key);
        lastSeen.put(event.kind(), event.atMillis());
        return false;
    }

    private final Map<EventKind, String> lastKeys = new LinkedHashMap<>();

    /** Recent dispatched events, oldest first, bounded. */
    public List<ClientEvent> history() {
        return new ArrayList<>(history);
    }

    /** Recent events of one kind, oldest first. */
    public List<ClientEvent> history(EventKind kind) {
        List<ClientEvent> result = new ArrayList<>();
        for (ClientEvent event : history) {
            if (event.kind() == kind) {
                result.add(event);
            }
        }
        return result;
    }

    public void clearHistory() {
        history.clear();
    }

    public long publishedTotal() {
        return publishedTotal;
    }

    public long suppressedTotal() {
        return suppressedTotal;
    }

    public int subscriberCount() {
        return registrations.size();
    }

    /** A one-line summary for diagnostics. */
    public String summary() {
        return "events: " + publishedTotal + " published, " + suppressedTotal + " suppressed, "
                + registrations.size() + " subscriber(s)";
    }
}
