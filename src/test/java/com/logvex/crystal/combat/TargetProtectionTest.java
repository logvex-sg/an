package com.logvex.crystal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies the pure enchantment-protection arithmetic used by the damage model. */
class TargetProtectionTest {

    @Test
    void blastProtectionContributesTwoEpfPerLevel() {
        assertEquals(0, TargetProtection.protectionEpf(0, 0), 1e-9);
        assertEquals(2, TargetProtection.protectionEpf(0, 1), 1e-9);
        assertEquals(8, TargetProtection.protectionEpf(0, 4), 1e-9);
    }

    @Test
    void generalProtectionContributesOneEpfPerLevel() {
        assertEquals(4, TargetProtection.protectionEpf(4, 0), 1e-9);
    }

    @Test
    void bothEnchantmentsSum() {
        // Protection IV (4) + Blast Protection IV (8) = 12, below the game's cap of 20.
        assertEquals(12, TargetProtection.protectionEpf(4, 4), 1e-9);
    }

    @Test
    void negativeLevelsContributeNothing() {
        assertEquals(0, TargetProtection.protectionEpf(-2, -3), 1e-9);
    }

    @Test
    void aFullBlastProtectionSetReducesDamageByEpfOverTwentyFive() {
        double epf = TargetProtection.protectionEpf(0, 4);
        // 8/25 = 32% reduction, so 68% of the post-armour damage survives.
        assertEquals(0.68, CrystalDamage.afterProtection(1.0, epf), 1e-9);
    }
}
