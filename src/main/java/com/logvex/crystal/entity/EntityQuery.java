package com.logvex.crystal.entity;

import com.logvex.crystal.client.CrystalClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A cheap, shared snapshot of the entities the client can currently see, normalized into
 * {@link EntityInfo} values.
 *
 * <p>This is the entity counterpart to {@code WorldQuery}: several modules want "the
 * players/mobs/items around me, with health and distance". Rebuilding that list, entity by
 * entity, in every module every frame is wasteful, so one snapshot is rebuilt on an interval
 * and shared.
 *
 * <p>Threading mirrors {@code WorldQuery}: the snapshot is published copy-on-write and the
 * reference is {@code volatile}, so the client thread can rebuild while the render thread
 * reads without locking. Because the published list holds only immutable values, a reader
 * can never touch an entity that has since been removed or had its id reused.
 *
 * <p>The snapshot is cleared the moment no world is loaded (disconnect, dimension change
 * handled by the caller's tick), so no entity reference outlives its world.
 */
public final class EntityQuery {

    private static EntityQuery instance;

    private volatile List<EntityInfo> snapshot = List.of();
    private volatile long lastScanMillis;
    private int intervalMs = 100;

    private EntityQuery() {
    }

    /** The shared instance, created on first use. */
    public static synchronized EntityQuery get() {
        if (instance == null) {
            instance = new EntityQuery();
        }
        return instance;
    }

    /** Minimum interval between snapshot rebuilds, in milliseconds. */
    public void setIntervalMs(int intervalMs) {
        this.intervalMs = Math.max(20, intervalMs);
    }

    public int intervalMs() {
        return intervalMs;
    }

    /**
     * Rebuilds the snapshot when the interval has elapsed and a world is loaded. Clears it
     * immediately when no world is loaded, so a disconnect cannot leave stale entities.
     */
    public void tick(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            if (!snapshot.isEmpty()) {
                snapshot = List.of();
                lastScanMillis = 0;
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScanMillis < intervalMs) {
            return;
        }
        lastScanMillis = now;
        snapshot = scan(client, now);
    }

    private static List<EntityInfo> scan(MinecraftClient client, long now) {
        PlayerEntity local = client.player;
        List<EntityInfo> result = new ArrayList<>();
        CrystalClient bootstrap = CrystalClient.get();
        for (Entity entity : client.world.getEntities()) {
            if (entity == null || entity == local || entity.isRemoved()) {
                continue;
            }
            result.add(snapshot(entity, local, bootstrap, now));
        }
        return List.copyOf(result);
    }

    private static EntityInfo snapshot(Entity entity, PlayerEntity local, CrystalClient bootstrap, long now) {
        EntityCategory category = EntityCategory.of(entity);
        boolean living = entity instanceof LivingEntity;
        float health = 0;
        float maxHealth = 0;
        int armor = 0;
        if (living) {
            LivingEntity livingEntity = (LivingEntity) entity;
            health = livingEntity.getHealth();
            maxHealth = livingEntity.getMaxHealth();
            armor = livingEntity.getArmor();
        }
        boolean friend = bootstrap != null && category == EntityCategory.PLAYER
                && bootstrap.friends().isFriend(entity.getName().getString());
        Vec3d pos = entity.getEntityPos();
        Vec3d velocity = entity.getVelocity();
        double distance = local.distanceTo(entity);
        boolean los = false;
        try {
            los = local.canSee(entity);
        } catch (RuntimeException ignored) {
            // canSee can throw for an entity being removed this tick; treat as no line of sight.
        }
        return new EntityInfo(entity.getUuid(), entity.getId(), category, entity.getName().getString(),
                pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, distance,
                entity.getBoundingBox(), health, maxHealth, armor, living, friend,
                entity.isInvisible(), los, now);
    }

    /** Drops the snapshot immediately (e.g. on disconnect). */
    public void invalidate() {
        snapshot = List.of();
        lastScanMillis = 0;
    }

    /** The current snapshot. Read-only; do not retain across ticks. */
    public List<EntityInfo> all() {
        return snapshot;
    }

    /** Entities in the snapshot matching {@code filter}, nearest first. */
    public List<EntityInfo> matching(EntityFilter filter) {
        List<EntityInfo> result = new ArrayList<>();
        for (EntityInfo info : snapshot) {
            if (filter == null || filter.accepts(info.category(), false, info.friend(),
                    info.invisible(), info.living(), info.distance(), info.name())) {
                result.add(info);
            }
        }
        result.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        return result;
    }

    /** The nearest entity of any of {@code categories} within {@code maxDistance}, or null. */
    public EntityInfo nearest(EntityCategory category, double maxDistance) {
        EntityInfo best = null;
        for (EntityInfo info : snapshot) {
            if (info.category() != category || info.distance() > maxDistance) {
                continue;
            }
            if (best == null || info.distance() < best.distance()) {
                best = info;
            }
        }
        return best;
    }

    /** Counts of entities per category in the current snapshot. */
    public java.util.Map<EntityCategory, Integer> counts() {
        java.util.Map<EntityCategory, Integer> counts = new java.util.EnumMap<>(EntityCategory.class);
        for (EntityInfo info : snapshot) {
            counts.merge(info.category(), 1, Integer::sum);
        }
        return counts;
    }

    public int size() {
        return snapshot.size();
    }

    /** Milliseconds since the snapshot was last rebuilt, or -1 before the first scan. */
    public long ageMillis() {
        return lastScanMillis == 0 ? -1 : System.currentTimeMillis() - lastScanMillis;
    }

    /** Immutable view for callers that want to iterate without copying again. */
    public List<EntityInfo> view() {
        return Collections.unmodifiableList(snapshot);
    }
}
