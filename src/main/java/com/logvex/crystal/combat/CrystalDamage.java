package com.logvex.crystal.combat;

/**
 * Pure model of Minecraft's explosion damage, used by the HUD to predict end-crystal
 * damage to a target and to the local player.
 *
 * <p>This mirrors the vanilla formulas closely enough to be useful in a fight and is
 * deliberately independent of Minecraft classes so it can be unit tested:
 *
 * <pre>
 *   intensity = (1 - distance / (power * 2)) * exposure
 *   raw       = (intensity^2 + intensity) / 2 * 7 * power * 2 + 1
 *   afterArmor= raw * (1 - min(20, max(armor/5, armor - raw / (2 + toughness/4))) / 25)
 * </pre>
 *
 * <p>{@code exposure} (0..1) is how much of the blast the entity actually absorbs, which
 * in the real game depends on block occlusion via raycasts. The client cannot cheaply
 * reproduce that for every entity, so callers pass a best-effort exposure (see
 * {@link #DEFAULT_EXPOSURE}); a conservative default keeps the estimate honest rather
 * than optimistic.
 */
public final class CrystalDamage {

    /** End-crystal explosion power (the vanilla value). */
    public static final double END_CRYSTAL_POWER = 6.0;

    /**
     * A neutral exposure assumption. Full exposure over-predicts damage through walls;
     * this value keeps the estimate closer to typical crystal-PvP geometry.
     */
    public static final double DEFAULT_EXPOSURE = 0.6;

    private CrystalDamage() {
    }

    /**
     * Raw explosion damage at {@code distance} blocks from a blast of the given power.
     * Returns 0 beyond the blast radius.
     */
    public static double rawDamage(double distance, double power, double exposure) {
        if (power <= 0 || distance < 0) {
            return 0;
        }
        double radius = power * 2.0;
        if (distance > radius) {
            return 0;
        }
        double exposureClamped = Math.max(0, Math.min(1, exposure));
        double intensity = (1.0 - distance / radius) * exposureClamped;
        return (intensity * intensity + intensity) / 2.0 * 7.0 * radius + 1.0;
    }

    /** Convenience overload using the end-crystal power and the default exposure. */
    public static double rawCrystalDamage(double distance) {
        return rawDamage(distance, END_CRYSTAL_POWER, DEFAULT_EXPOSURE);
    }

    /**
     * Applies armour and toughness reduction (the 1.9+ formula). Absorption and
     * enchantment protection are not modelled here; callers that know them subtract after.
     */
    public static double afterArmor(double raw, double armor, double toughness) {
        if (raw <= 0) {
            return 0;
        }
        double a = Math.max(0, armor);
        double t = Math.max(0, toughness);
        double reduced = Math.min(20.0, Math.max(a / 5.0, a - raw / (2.0 + t / 4.0)));
        double factor = 1.0 - reduced / 25.0;
        return raw * Math.max(0, factor);
    }

    /** Full evaluation against a target with the given armour, toughness and health. */
    public static DamageResult evaluate(double distance, double armor, double toughness,
                                        double health, double exposure) {
        double raw = rawDamage(distance, END_CRYSTAL_POWER, exposure);
        double reduced = afterArmor(raw, armor, toughness);
        return new DamageResult(raw, reduced, health, reduced >= health);
    }

    /** Evaluation using the default exposure. */
    public static DamageResult evaluate(double distance, double armor, double toughness, double health) {
        return evaluate(distance, armor, toughness, health, DEFAULT_EXPOSURE);
    }

    /**
     * A coarse exposure estimate from whether the blast has a clear line of sight to the
     * entity. Occluded blasts are heavily damped; the value is intentionally simple so it
     * never costs a raycast per entity.
     */
    public static double exposureFor(boolean lineOfSight) {
        return lineOfSight ? 0.85 : 0.3;
    }
}
