package com.logvex.crystal.world;

import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;

/**
 * A cheap, shared snapshot of the loaded block entities.
 *
 * <p>Several block-ESP and world-info modules want "the containers/signs/spawners around
 * me". Rebuilding that list in every module, every frame, is wasteful; instead this keeps
 * one snapshot, refreshed on an interval, that every reader shares.
 *
 * <p>Only block entities in loaded chunks are ever present (the client only tracks those),
 * so a module reading this cannot claim to see into an unloaded chunk.
 *
 * <p>The snapshot is published copy-on-write: a rebuild builds a fresh immutable list and
 * swaps the reference, and readers take one reference per frame. The tick runs on the client
 * thread and the render thread reads concurrently, so the reference swap must be atomic
 * (hence {@code volatile}) and the published list must never be mutated afterwards.
 */
public final class WorldQuery {

    private static WorldQuery instance;

    private volatile List<BlockEntity> snapshot = List.of();

    private long lastScanMillis;
    private int intervalMs = 250;

    private WorldQuery() {
    }

    /** The shared instance, created on first use. */
    public static synchronized WorldQuery get() {
        if (instance == null) {
            instance = new WorldQuery();
        }
        return instance;
    }

    /** Minimum interval between snapshot rebuilds, in milliseconds. */
    public void setIntervalMs(int intervalMs) {
        this.intervalMs = Math.max(50, intervalMs);
    }

    /** Refreshes the snapshot when the interval has elapsed and a world is loaded. */
    public void tick(MinecraftClient client) {
        if (client == null || client.world == null) {
            if (!snapshot.isEmpty()) {
                snapshot = List.of();
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < intervalMs) {
            return;
        }
        lastScanMillis = now;
        snapshot = List.copyOf(client.world.getBlockEntities());
    }

    /** Drops the snapshot immediately (e.g. on disconnect). */
    public void invalidate() {
        snapshot = List.of();
        lastScanMillis = 0;
    }

    /** The current snapshot. Read-only; do not retain across ticks. */
    public List<BlockEntity> blockEntities() {
        return snapshot;
    }

    public int size() {
        return snapshot.size();
    }

    /** Milliseconds since the snapshot was last rebuilt, or -1 before the first scan. */
    public long ageMillis() {
        return lastScanMillis == 0 ? -1 : System.currentTimeMillis() - lastScanMillis;
    }
}
