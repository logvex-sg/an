package com.logvex.crystal.combat;

import net.minecraft.entity.LivingEntity;

/**
 * A snapshot of the entity the client is currently treating as the combat target.
 * Immutable so the HUD can hold it across a frame without the entity mutating underneath.
 *
 * @param entity        the live entity reference (identity only; not cached data)
 * @param name          display name
 * @param health        current health
 * @param maxHealth     maximum health
 * @param armor         armour points
 * @param distance      distance from the local player in blocks
 * @param hasLineOfSight whether the local player can see the target
 */
public record CombatTarget(LivingEntity entity, String name, float health, float maxHealth,
                           int armor, double distance, boolean hasLineOfSight) {

    /** Health as a 0..1 fraction, guarding against a zero maximum. */
    public float healthFraction() {
        return maxHealth <= 0 ? 0f : Math.max(0f, Math.min(1f, health / maxHealth));
    }

    /** True when the target is close enough to be a real threat. */
    public boolean inRange(double range) {
        return distance <= range;
    }
}
