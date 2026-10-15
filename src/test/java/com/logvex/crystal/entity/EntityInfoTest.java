package com.logvex.crystal.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;

/** Unit tests for the derived values on {@link EntityInfo}. */
class EntityInfoTest {

    private static EntityInfo living(float health, float maxHealth) {
        return new EntityInfo(UUID.randomUUID(), 1, EntityCategory.PLAYER, "Steve",
                0, 0, 0, 0.1, 0, 0.2, 5, new Box(0, 0, 0, 1, 1, 1),
                health, maxHealth, 8, true, false, false, true, 0);
    }

    @Test
    void healthFractionIsClampedToUnitRange() {
        assertEquals(0.5, living(10, 20).healthFraction(), 1e-9);
        assertEquals(0.0, living(0, 20).healthFraction(), 1e-9);
        assertEquals(1.0, living(30, 20).healthFraction(), 1e-9);
    }

    @Test
    void healthFractionIsZeroForNonLiving() {
        EntityInfo item = new EntityInfo(UUID.randomUUID(), 2, EntityCategory.ITEM, "diamond",
                0, 0, 0, 0, 0, 0, 3, new Box(0, 0, 0, 0.25, 0.25, 0.25),
                0, 0, 0, false, false, false, true, 0);
        assertEquals(0.0, item.healthFraction(), 1e-9);
    }

    @Test
    void horizontalSpeedConvertsTicksToSeconds() {
        // 0.1 and 0.2 blocks/tick -> magnitude 0.2236 -> ~4.47 blocks/second.
        assertEquals(Math.hypot(0.1, 0.2) * 20.0, living(20, 20).horizontalSpeed(), 1e-9);
    }

    @Test
    void zeroMaxHealthDoesNotDivideByZero() {
        assertEquals(0.0, living(5, 0).healthFraction(), 1e-9);
    }
}
