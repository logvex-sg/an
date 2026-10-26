package com.logvex.crystal.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks entity lifecycle across the shared {@link EntityQuery} snapshots: which entities
 * appeared, which disappeared, and a bounded recent history for each one.
 *
 * <p>This is the entity counterpart to a "last seen" table. It never holds an {@code Entity}
 * - only the immutable {@link EntityInfo} values - so an entity that is removed between
 * snapshots can never be read through a stale reference or a reused runtime id. Everything
 * is keyed by the stable {@link UUID}.
 *
 * <p>The tracker is deliberately Minecraft-independent apart from {@link EntityInfo}: it is
 * fed a list of snapshots and produces diffs, so the spawn/despawn/retention logic is unit
 * testable without a running client. It runs on the client thread; {@link #clear()} is
 * called on a disconnect or dimension change so nothing outlives its world.
 */
public final class EntityTracker {

    /** What happened to an entity between two snapshots. */
    public enum Kind {
        /** The entity was not in the previous snapshot. */
        SPAWN,
        /** The entity was in the previous snapshot but not the current one. */
        DESPAWN
    }

    /** A lifecycle event, carrying the snapshot that produced it. */
    public record EntityEvent(EntityInfo info, Kind kind, long atMillis) {
    }

    private static final int MAX_EVENTS = 128;

    private final int maxTracked;
    private final int historyPerEntity;

    private final Map<UUID, EntityInfo> current = new LinkedHashMap<>();
    // A bounded, access-ordered map so recently inspected entities survive while the
    // long tail of everything else seen this session is evicted. Insertion order is the
    // eviction order; a history()/lastObservedMillis() read refreshes recency.
    private final Map<UUID, Deque<EntityInfo>> history =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Deque<EntityInfo>> eldest) {
                    return size() > maxTracked;
                }
            };
    private final Map<UUID, Long> lastObserved = new HashMap<>();
    private final Deque<EntityEvent> events = new ArrayDeque<>();

    private int spawnedTotal;
    private int despawnedTotal;

    /**
     * @param maxTracked       most entities remembered at once (oldest-seen evicted first)
     * @param historyPerEntity most snapshots kept per entity
     */
    public EntityTracker(int maxTracked, int historyPerEntity) {
        this.maxTracked = Math.max(1, maxTracked);
        this.historyPerEntity = Math.max(1, historyPerEntity);
    }

    /** A tracker with sane defaults: 512 tracked entities, 16 snapshots of history each. */
    public EntityTracker() {
        this(512, 16);
    }

    /**
     * Diffs a fresh snapshot against the previous one and records the events.
     *
     * @param snapshot the entities the client can currently see
     * @param now      wall-clock time of the snapshot, for {@link #lastObserved}
     */
    public void update(List<EntityInfo> snapshot, long now) {
        Map<UUID, EntityInfo> incoming = new LinkedHashMap<>();
        if (snapshot != null) {
            for (EntityInfo info : snapshot) {
                incoming.put(info.uuid(), info);
            }
        }

        // Spawns and refreshes: everything in the new snapshot.
        for (Map.Entry<UUID, EntityInfo> entry : incoming.entrySet()) {
            UUID uuid = entry.getKey();
            EntityInfo info = entry.getValue();
            if (!current.containsKey(uuid)) {
                spawnedTotal++;
                record(new EntityEvent(info, Kind.SPAWN, now));
            }
            lastObserved.put(uuid, now);
            pushHistory(uuid, info);
        }

        // Despawns: anything we had that is now gone. The history is kept (the entity
        // browser shows a despawned entity's last snapshot) but stays bounded by the
        // access-ordered eviction in the history map. The last-observed stamp is only ever
        // read for currently-tracked entities, so it is dropped here to keep its map the
        // exact size of the live set.
        for (EntityInfo previous : current.values()) {
            if (!incoming.containsKey(previous.uuid())) {
                despawnedTotal++;
                record(new EntityEvent(previous, Kind.DESPAWN, now));
                lastObserved.remove(previous.uuid());
            }
        }

        current.clear();
        current.putAll(incoming);
        evictOverflow();
    }

    private void pushHistory(UUID uuid, EntityInfo info) {
        Deque<EntityInfo> deque = history.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        deque.addLast(info);
        while (deque.size() > historyPerEntity) {
            deque.removeFirst();
        }
    }

    private void record(EntityEvent event) {
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    /** Keeps the tracked set bounded: the entities seen longest ago are dropped first. */
    private void evictOverflow() {
        if (current.size() <= maxTracked) {
            return;
        }
        List<UUID> byAge = new ArrayList<>(current.keySet());
        byAge.sort((a, b) -> Long.compare(lastObserved.getOrDefault(a, 0L), lastObserved.getOrDefault(b, 0L)));
        int drop = current.size() - maxTracked;
        for (int i = 0; i < drop; i++) {
            UUID uuid = byAge.get(i);
            current.remove(uuid);
            history.remove(uuid);
            lastObserved.remove(uuid);
        }
    }

    /** Events recorded since the last drain, oldest first, then clears the queue. */
    public List<EntityEvent> drainEvents() {
        List<EntityEvent> copy = new ArrayList<>(events);
        events.clear();
        return copy;
    }

    /** Events recorded since the last drain, without clearing them. */
    public List<EntityEvent> peekEvents() {
        return new ArrayList<>(events);
    }

    /** The most recent snapshot for an entity, or null when it is not currently tracked. */
    public EntityInfo lastSeen(UUID uuid) {
        return current.get(uuid);
    }

    /** Retained snapshots for an entity, oldest first. Empty when unknown. */
    public List<EntityInfo> history(UUID uuid) {
        Deque<EntityInfo> deque = history.get(uuid);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    /** Wall-clock time the entity was last observed, or -1 when unknown. */
    public long lastObservedMillis(UUID uuid) {
        return lastObserved.getOrDefault(uuid, -1L);
    }

    public boolean isTracked(UUID uuid) {
        return current.containsKey(uuid);
    }

    public int trackedCount() {
        return current.size();
    }

    /**
     * Distinct entities the tracker still holds any per-entity state for (current snapshots
     * plus retained history). Bounded by {@code maxTracked}; used by tests and diagnostics to
     * prove a long session cannot grow the maps without limit.
     */
    public int rememberedEntities() {
        java.util.Set<UUID> ids = new java.util.HashSet<>(current.keySet());
        ids.addAll(history.keySet());
        ids.addAll(lastObserved.keySet());
        return ids.size();
    }

    public int spawnedTotal() {
        return spawnedTotal;
    }

    public int despawnedTotal() {
        return despawnedTotal;
    }

    /** The currently tracked snapshots, insertion-ordered. */
    public List<EntityInfo> tracked() {
        return new ArrayList<>(current.values());
    }

    /** Clears all state. Called on disconnect/dimension change. */
    public void clear() {
        current.clear();
        history.clear();
        lastObserved.clear();
        events.clear();
        spawnedTotal = 0;
        despawnedTotal = 0;
    }
}
