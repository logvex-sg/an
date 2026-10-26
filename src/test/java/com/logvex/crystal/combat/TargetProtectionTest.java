package com.logvex.crystal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies the pure enchantment-protection arithmetic used by the damage model. */
class TargetProtectionTest {

    @Test
    void blastProtectionContributesFourEpfPerLevel() {
        assertEquals(0, TargetProtection.protectionEpf(0, 0), 1e-9);
        assertEquals(4, TargetProtection.protectionEpf(0, 1), 1e-9);
        assertEquals(16, TargetProtection.protectionEpf(0, 4), 1e-9);
    }

    @Test
    void generalProtectionContributesOneEpfPerLevel() {
        assertEquals(4, TargetProtection.protectionEpf(4, 0), 1e-9);
    }

    @Test
    void bothEnchantmentsSum() {
        // Protection IV (4) + Blast Protection IV (16) = 20, the game's cap.
        assertEquals(20, TargetProtection.protectionEpf(4, 4), 1e-9);
    }

    @Test
    void negativeLevelsContributeNothing() {
        assertEquals(0, TargetProtection.protectionEpf(-2, -3), 1e-9);
    }

    @Test
    void aFullProtectionSetReducesDamageToTheCappedFraction() {
        double epf = TargetProtection.protectionEpf(4, 4);
        // 20/25 = 80% reduction, i.e. 20% of the post-armour damage survives.
        assertEquals(0.2, CrystalDamage.afterProtection(1.0, epf), 1e-9);
    }
}
