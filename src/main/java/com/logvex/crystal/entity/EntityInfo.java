package com.logvex.crystal.entity;

import java.util.UUID;
import net.minecraft.util.math.Box;

/**
 * A normalized, immutable snapshot of one entity at the moment the shared
 * {@link EntityQuery} last scanned the world.
 *
 * <p>It is deliberately a <em>value</em>, not a reference: it holds no {@code Entity}, so a
 * consumer that keeps one across frames cannot pin a removed entity or read a stale field
 * through a reused runtime id. Everything a HUD or ESP needs (position, box, health,
 * category, friend status) is copied in at scan time.
 *
 * @param uuid          the entity's stable UUID
 * @param runtimeId     the entity's runtime (session) id, for de-duplication within a frame
 * @param category      the coarse category
 * @param name          the display name
 * @param x             world x
 * @param y             world y
 * @param z             world z
 * @param velocityX     velocity x (blocks/tick)
 * @param velocityY     velocity y
 * @param velocityZ     velocity z
 * @param distance      distance from the local player
 * @param box           the bounding box
 * @param health        current health for a living entity, else 0
 * @param maxHealth     maximum health for a living entity, else 0
 * @param armor         armour points for a living entity, else 0
 * @param living        whether the entity is a living entity
 * @param friend        whether the entity is on the friend list
 * @param invisible     whether the entity is invisible
 * @param lineOfSight   whether the local player can see the entity
 * @param observedMillis wall-clock time this snapshot was taken
 */
public record EntityInfo(UUID uuid, int runtimeId, EntityCategory category, String name,
                         double x, double y, double z,
                         double velocityX, double velocityY, double velocityZ,
                         double distance, Box box,
                         float health, float maxHealth, int armor,
                         boolean living, boolean friend, boolean invisible, boolean lineOfSight,
                         long observedMillis) {

    /** Health as a 0..1 fraction, or 0 for a non-living entity. */
    public double healthFraction() {
        return living && maxHealth > 0 ? Math.max(0, Math.min(1, health / maxHealth)) : 0;
    }

    /** Horizontal speed in blocks per second (velocity is per tick, 20 ticks/second). */
    public double horizontalSpeed() {
        return Math.sqrt(velocityX * velocityX + velocityZ * velocityZ) * 20.0;
    }
}
