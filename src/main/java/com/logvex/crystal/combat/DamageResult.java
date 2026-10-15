package com.logvex.crystal.combat;

/**
 * The outcome of evaluating an explosion against an entity: the raw damage the game
 * computes, the damage left after armour, and whether it would be lethal to the health
 * supplied.
 *
 * @param raw         damage before armour reduction
 * @param afterArmor  damage after armour/toughness reduction
 * @param healthBefore health the entity had going in
 * @param lethal      whether {@code afterArmor} meets or exceeds {@code healthBefore}
 */
public record DamageResult(double raw, double afterArmor, double healthBefore, boolean lethal) {

    /** Damage actually applied, floored at zero. */
    public double applied() {
        return Math.max(0, afterArmor);
    }

    /** Health the entity would be left with, floored at zero. */
    public double remainingHealth() {
        return Math.max(0, healthBefore - applied());
    }

    /** True when no meaningful damage is dealt. */
    public boolean negligible() {
        return applied() < 0.5;
    }
}
