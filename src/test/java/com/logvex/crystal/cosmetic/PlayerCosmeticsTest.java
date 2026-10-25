package com.logvex.crystal.cosmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The cosmetic gate is the only thing standing between the renderer and drawing geometry, so
 * it is exercised directly: the accessory must be inert unless the module is on, the theme's
 * cosmetic gate is on, and a real style is selected.
 */
class PlayerCosmeticsTest {

    @AfterEach
    void reset() {
        PlayerCosmetics.setEnabled(false);
        PlayerCosmetics.setAnimate(true);
        PlayerCosmetics.setGlow(0.5f);
        PlayerCosmetics.setStyle(PlayerCosmetics.Style.CAT_EARS);
        PlayerCosmetics.setCuteGate(false);
    }

    @Test
    void inertByDefault() {
        assertFalse(PlayerCosmetics.visible());
        assertFalse(PlayerCosmetics.animated());
    }

    @Test
    void needsModuleAndThemeGateAndAStyle() {
        PlayerCosmetics.setEnabled(true);
        PlayerCosmetics.setCuteGate(true);
        assertTrue(PlayerCosmetics.visible());

        PlayerCosmetics.setStyle(PlayerCosmetics.Style.NONE);
        assertFalse(PlayerCosmetics.visible(), "NONE style must draw nothing");

        PlayerCosmetics.setStyle(PlayerCosmetics.Style.HALO);
        PlayerCosmetics.setCuteGate(false);
        assertFalse(PlayerCosmetics.visible(), "theme gate off must hide the cosmetic");

        PlayerCosmetics.setCuteGate(true);
        PlayerCosmetics.setEnabled(false);
        assertFalse(PlayerCosmetics.visible(), "module off must hide the cosmetic");
    }

    @Test
    void reducedMotionFreezesButDoesNotHide() {
        PlayerCosmetics.setEnabled(true);
        PlayerCosmetics.setCuteGate(true);
        PlayerCosmetics.setAnimate(false);
        assertTrue(PlayerCosmetics.visible());
        assertFalse(PlayerCosmetics.animated());
    }

    @Test
    void glowIsClamped() {
        PlayerCosmetics.setGlow(-1f);
        assertEquals(0f, PlayerCosmetics.glow(), 1e-6);
        PlayerCosmetics.setGlow(5f);
        assertEquals(1f, PlayerCosmetics.glow(), 1e-6);
    }

    @Test
    void overlayAlphaTracksGlow() {
        PlayerCosmetics.setGlow(0f);
        assertEquals(0.25f, PlayerCosmetics.overlayAlpha(), 1e-6);
        PlayerCosmetics.setGlow(1f);
        assertEquals(0.75f, PlayerCosmetics.overlayAlpha(), 1e-6);
    }

    @Test
    void swayStaysWithinItsAmplitude() {
        PlayerCosmetics.setGlow(0.5f);
        float max = 0.05f + 0.05f * 0.5f;
        for (int tick = 0; tick < 400; tick++) {
            float s = PlayerCosmetics.sway(tick, 0f);
            assertTrue(Math.abs(s) <= max + 1e-6, "sway " + s + " exceeds " + max);
        }
    }

    @Test
    void swayIsPeriodic() {
        PlayerCosmetics.setGlow(0.5f);
        int period = Math.round(PlayerCosmetics.SWAY_PERIOD_TICKS);
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(PlayerCosmetics.sway(tick, 0f), PlayerCosmetics.sway(tick + period, 0f), 1e-4);
        }
    }
}
