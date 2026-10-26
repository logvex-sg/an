package com.logvex.crystal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the crystal-damage model against the vanilla explosion formula. These numbers come
 * from the published formula, not from a client, so a regression in the maths is caught
 * here rather than in a fight.
 */
class CrystalDamageTest {

    @Test
    void noDamageBeyondBlastRadius() {
        // Radius for an end crystal is power * 2 = 12 blocks.
        assertEquals(0, CrystalDamage.rawDamage(12.5, CrystalDamage.END_CRYSTAL_POWER, 1.0));
        assertEquals(0, CrystalDamage.rawDamage(0, 0, 1.0));
        assertEquals(0, CrystalDamage.rawDamage(-1, CrystalDamage.END_CRYSTAL_POWER, 1.0));
    }

    @Test
    void damageFallsOffWithDistance() {
        double near = CrystalDamage.rawDamage(1.0, CrystalDamage.END_CRYSTAL_POWER, 1.0);
        double far = CrystalDamage.rawDamage(9.0, CrystalDamage.END_CRYSTAL_POWER, 1.0);
        assertTrue(near > far);
        assertTrue(far > 0);
    }

    @Test
    void fullExposureAtZeroDistanceMatchesFormula() {
        // intensity = 1, so raw = (1 + 1)/2 * 7 * 12 + 1 = 85.
        assertEquals(85.0, CrystalDamage.rawDamage(0, CrystalDamage.END_CRYSTAL_POWER, 1.0), 1e-9);
    }

    @Test
    void exposureScalesDamage() {
        double full = CrystalDamage.rawDamage(4.0, CrystalDamage.END_CRYSTAL_POWER, 1.0);
        double half = CrystalDamage.rawDamage(4.0, CrystalDamage.END_CRYSTAL_POWER, 0.5);
        assertTrue(full > half);
        double occluded = CrystalDamage.exposureFor(false);
        double visible = CrystalDamage.exposureFor(true);
        assertTrue(visible > occluded);
    }

    @Test
    void armourReducesButNeverInvertsDamage() {
        double raw = 40.0;
        assertEquals(raw, CrystalDamage.afterArmor(raw, 0, 0), 1e-9);
        double armored = CrystalDamage.afterArmor(raw, 20, 8);
        assertTrue(armored < raw);
        assertTrue(armored > 0);
        assertEquals(0, CrystalDamage.afterArmor(0, 20, 8), 1e-9);
        // Reduction is capped at 20 points of the 25-point scale.
        assertTrue(CrystalDamage.afterArmor(1000, 1000, 0) >= raw * 0.19);
    }

    @Test
    void evaluateFlagsLethalHits() {
        // Point blank, full exposure, no armour: 85 damage kills a 20 hp player.
        DamageResult lethal = CrystalDamage.evaluate(0, 0, 0, 20, 1.0);
        assertTrue(lethal.lethal());
        assertEquals(0, lethal.remainingHealth(), 1e-9);
        assertFalse(lethal.negligible());

        // Far away with heavy armour: survivable.
        DamageResult survivable = CrystalDamage.evaluate(11.5, 20, 12, 20, 0.3);
        assertFalse(survivable.lethal());
        assertTrue(survivable.applied() < 20);
    }

    @Test
    void negligibleDamageIsReported() {
        DamageResult result = CrystalDamage.evaluate(11.99, 20, 12, 20, 0.1);
        assertTrue(result.negligible());
        assertEquals(0, result.remainingHealth() >= 0 ? 0 : 1, 1e-9);
    }

    @Test
    void resistanceReducesDamageByTwentyPercentPerLevel() {
        double raw = 40.0;
        // Amplifier 0 is Resistance I: a 20% reduction.
        assertEquals(raw * 0.8, CrystalDamage.afterResistance(raw, 0), 1e-9);
        assertEquals(raw * 0.6, CrystalDamage.afterResistance(raw, 1), 1e-9);
        assertEquals(raw * 0.4, CrystalDamage.afterResistance(raw, 2), 1e-9);
        assertEquals(raw * 0.2, CrystalDamage.afterResistance(raw, 3), 1e-9);
        // Level V (amplifier 4) is full immunity, and it never goes negative.
        assertEquals(0, CrystalDamage.afterResistance(raw, 4), 1e-9);
        assertEquals(0, CrystalDamage.afterResistance(raw, 9), 1e-9);
        assertEquals(0, CrystalDamage.afterResistance(0, 2), 1e-9);
    }

    @Test
    void protectionReducesDamageByEpfOverTwentyFive() {
        double raw = 40.0;
        assertEquals(raw, CrystalDamage.afterProtection(raw, 0), 1e-9);
        assertEquals(raw * (1 - 8 / 25.0), CrystalDamage.afterProtection(raw, 8), 1e-9);
        // Capped at 20 points, so 25+ epf behaves like 20.
        assertEquals(CrystalDamage.afterProtection(raw, 20), CrystalDamage.afterProtection(raw, 100), 1e-9);
        assertEquals(raw * 0.2, CrystalDamage.afterProtection(raw, 20), 1e-9);
        assertEquals(0, CrystalDamage.afterProtection(0, 8), 1e-9);
    }

    @Test
    void fullEvaluationAppliesResistanceAndProtectionAfterArmour() {
        double armorOnly = CrystalDamage.evaluate(3, 20, 8, 20, 1.0).applied();
        double withResistance = CrystalDamage.evaluate(3, 20, 8, 20, 1.0, 0, 0).applied();
        // Resistance I (amplifier 0) is a further 20% off the post-armour value.
        assertEquals(armorOnly * 0.8, withResistance, 1e-9);
        // Protection 8 is another 32% off.
        double withBoth = CrystalDamage.evaluate(3, 20, 8, 20, 1.0, 0, 8).applied();
        assertEquals(armorOnly * 0.8 * (1 - 8 / 25.0), withBoth, 1e-9);
    }
}
