package com.logvex.crystal.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;

/** Tests for {@link EntityTracker}: spawn/despawn diffing, history retention and bounds. */
class EntityTrackerTest {

    private static EntityInfo info(UUID uuid, String name) {
        return new EntityInfo(uuid, 1, EntityCategory.PLAYER, name,
                0, 0, 0, 0, 0, 0, 5, new Box(0, 0, 0, 1, 1, 1),
                20, 20, 0, true, false, false, true, 0);
    }

    @Test
    void firstSnapshotReportsEveryEntityAsSpawn() {
        EntityTracker tracker = new EntityTracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        tracker.update(List.of(info(a, "A"), info(b, "B")), 100);
        assertEquals(2, tracker.trackedCount());
        assertEquals(2, tracker.spawnedTotal());
        assertEquals(0, tracker.despawnedTotal());
        var events = tracker.drainEvents();
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> e.kind() == EntityTracker.Kind.SPAWN));
    }

    @Test
    void aVanishedEntityIsReportedAsDespawn() {
        EntityTracker tracker = new EntityTracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        tracker.update(List.of(info(a, "A"), info(b, "B")), 100);
        tracker.drainEvents();
        tracker.update(List.of(info(a, "A")), 200);
        var events = tracker.drainEvents();
        assertEquals(1, events.size());
        assertEquals(EntityTracker.Kind.DESPAWN, events.get(0).kind());
        assertEquals(b, events.get(0).info().uuid());
        assertEquals(1, tracker.despawnedTotal());
        assertFalse(tracker.isTracked(b));
    }

    @Test
    void drainClearsTheEventQueue() {
        EntityTracker tracker = new EntityTracker();
        tracker.update(List.of(info(UUID.randomUUID(), "A")), 1);
        assertEquals(1, tracker.drainEvents().size());
        assertEquals(0, tracker.drainEvents().size());
    }

    @Test
    void historyIsBoundedPerEntity() {
        EntityTracker tracker = new EntityTracker(512, 3);
        UUID a = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            tracker.update(List.of(info(a, "A")), i);
        }
        assertEquals(3, tracker.history(a).size());
    }

    @Test
    void trackedSetIsBoundedAndDropsOldestFirst() {
        EntityTracker tracker = new EntityTracker(2, 4);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        tracker.update(List.of(info(a, "A")), 10);
        tracker.update(List.of(info(a, "A"), info(b, "B")), 20);
        tracker.update(List.of(info(b, "B"), info(c, "C")), 30);
        // Only two may be tracked; the least recently observed (A) is evicted.
        assertEquals(2, tracker.trackedCount());
        assertFalse(tracker.isTracked(a));
        assertTrue(tracker.isTracked(c));
    }

    @Test
    void lastObservedTracksTheMostRecentSnapshotTime() {
        EntityTracker tracker = new EntityTracker();
        UUID a = UUID.randomUUID();
        tracker.update(List.of(info(a, "A")), 100);
        tracker.update(List.of(info(a, "A")), 250);
        assertEquals(250, tracker.lastObservedMillis(a));
        assertEquals(-1, tracker.lastObservedMillis(UUID.randomUUID()));
        assertNull(tracker.lastSeen(UUID.randomUUID()));
    }

    @Test
    void historyOfDespawnedEntitiesStaysBounded() {
        // A busy server churns thousands of entities through the client over a session. The
        // tracker must not retain per-entity history for entities long gone, or the maps grow
        // without limit; the bound is exercised by feeding more distinct entities than the
        // tracker is allowed to remember.
        int bound = 8;
        EntityTracker tracker = new EntityTracker(bound, 4);
        for (int i = 0; i < 500; i++) {
            tracker.update(List.of(info(UUID.randomUUID(), "E" + i)), i);
        }
        // Current snapshots plus retained history are each bounded by maxTracked, so the
        // total can never exceed twice that no matter how many entities pass through.
        assertTrue(tracker.rememberedEntities() <= 2 * bound,
                "history retained for " + tracker.rememberedEntities() + " entities");
    }

    @Test
    void historyOfARecentlySeenDespawnSurvivesEviction() {
        // The entity browser shows a despawned entity's last snapshot, so a recently seen
        // despawn must keep its history even while the long tail is evicted.
        EntityTracker tracker = new EntityTracker(4, 4);
        UUID watched = UUID.randomUUID();
        tracker.update(List.of(info(watched, "Watched")), 1);
        for (int i = 0; i < 200; i++) {
            tracker.update(List.of(info(UUID.randomUUID(), "E" + i)), 2 + i);
            // The browser reads the selected entity's history every frame, which keeps it warm.
            tracker.history(watched);
        }
        assertFalse(tracker.isTracked(watched));
        assertEquals(1, tracker.history(watched).size(),
                "the recently inspected despawn should keep its last snapshot");
    }

    @Test
    void clearResetsEverything() {
        EntityTracker tracker = new EntityTracker();
        tracker.update(List.of(info(UUID.randomUUID(), "A")), 1);
        tracker.clear();
        assertEquals(0, tracker.trackedCount());
        assertEquals(0, tracker.spawnedTotal());
        assertEquals(0, tracker.despawnedTotal());
        assertTrue(tracker.peekEvents().isEmpty());
    }
}
