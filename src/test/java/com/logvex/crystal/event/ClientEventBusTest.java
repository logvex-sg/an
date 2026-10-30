package com.logvex.crystal.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers dispatch, filtering, deduplication, bounded history and listener isolation. */
class ClientEventBusTest {

    private static ClientEvent event(EventKind kind, long at, String detail) {
        return ClientEvent.of(kind, at).put("d", detail).build();
    }

    @Test
    void deliversToEverySubscriber() {
        ClientEventBus bus = new ClientEventBus(0);
        List<ClientEvent> a = new ArrayList<>();
        List<ClientEvent> b = new ArrayList<>();
        bus.subscribe(a::add);
        bus.subscribe(b::add);
        assertTrue(bus.publish(event(EventKind.WORLD_JOINED, 1, "x")));
        assertEquals(1, a.size());
        assertEquals(1, b.size());
    }

    @Test
    void filtersByKind() {
        ClientEventBus bus = new ClientEventBus(0);
        List<ClientEvent> targetOnly = new ArrayList<>();
        bus.subscribe(EnumSet.of(EventKind.TARGET_CHANGED), targetOnly::add);
        bus.publish(event(EventKind.WORLD_JOINED, 1, "x"));
        bus.publish(event(EventKind.TARGET_CHANGED, 2, "y"));
        assertEquals(1, targetOnly.size());
        assertEquals(EventKind.TARGET_CHANGED, targetOnly.get(0).kind());
    }

    @Test
    void identicalEventWithinWindowIsSuppressed() {
        ClientEventBus bus = new ClientEventBus(1000);
        List<ClientEvent> seen = new ArrayList<>();
        bus.subscribe(seen::add);
        assertTrue(bus.publish(event(EventKind.TARGET_CHANGED, 100, "same")));
        assertFalse(bus.publish(event(EventKind.TARGET_CHANGED, 200, "same")), "duplicate suppressed");
        assertEquals(1, seen.size());
        assertEquals(1, bus.suppressedTotal());
        // After the window, the same state is reported again.
        assertTrue(bus.publish(event(EventKind.TARGET_CHANGED, 2000, "same")));
    }

    @Test
    void aChangedDetailIsNotSuppressed() {
        ClientEventBus bus = new ClientEventBus(1000);
        assertTrue(bus.publish(event(EventKind.TARGET_CHANGED, 100, "one")));
        assertTrue(bus.publish(event(EventKind.TARGET_CHANGED, 150, "two")));
    }

    @Test
    void differentKindsAreDeduplicatedIndependently() {
        ClientEventBus bus = new ClientEventBus(1000);
        assertTrue(bus.publish(event(EventKind.TARGET_CHANGED, 100, "x")));
        assertTrue(bus.publish(event(EventKind.PLAYER_HEALTH_CHANGED, 100, "x")));
    }

    @Test
    void throwingListenerDoesNotBreakDispatch() {
        ClientEventBus bus = new ClientEventBus(0);
        List<ClientEvent> seen = new ArrayList<>();
        bus.subscribe(e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(seen::add);
        assertTrue(bus.publish(event(EventKind.WORLD_JOINED, 1, "x")));
        assertEquals(1, seen.size(), "the healthy listener still receives the event");
    }

    @Test
    void closedSubscriptionStopsReceiving() {
        ClientEventBus bus = new ClientEventBus(0);
        List<ClientEvent> seen = new ArrayList<>();
        ClientEventBus.Subscription subscription = bus.subscribe(seen::add);
        bus.publish(event(EventKind.WORLD_JOINED, 1, "x"));
        subscription.close();
        bus.publish(event(EventKind.WORLD_LEFT, 2, "y"));
        assertEquals(1, seen.size());
        assertEquals(0, bus.subscriberCount());
    }

    @Test
    void historyIsBounded() {
        ClientEventBus bus = new ClientEventBus(0);
        for (int i = 0; i < 500; i++) {
            bus.publish(event(EventKind.MANUAL, i, "e" + i));
        }
        assertEquals(256, bus.history().size(), "history ring is bounded");
    }

    @Test
    void historyCanBeFilteredByKind() {
        ClientEventBus bus = new ClientEventBus(0);
        bus.publish(event(EventKind.WORLD_JOINED, 1, "a"));
        bus.publish(event(EventKind.MANUAL, 2, "b"));
        bus.publish(event(EventKind.MANUAL, 3, "c"));
        assertEquals(2, bus.history(EventKind.MANUAL).size());
    }

    @Test
    void nullEventIsIgnored() {
        ClientEventBus bus = new ClientEventBus(0);
        assertFalse(bus.publish(null));
        assertEquals(0, bus.publishedTotal());
    }
}
