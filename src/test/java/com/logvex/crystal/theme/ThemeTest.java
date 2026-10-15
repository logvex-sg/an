package com.logvex.crystal.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.util.ColorUtil;
import org.junit.jupiter.api.Test;

/**
 * Covers the theme layer: preset parsing, settings clamping, palette derivation and the
 * live-update contract of {@link ThemeManager}.
 */
class ThemeTest {

    @Test
    void presetParsesEnumNameLabelAndSpacing() {
        assertEquals(ThemePreset.SAKURA_PINK, ThemePreset.parse("SAKURA_PINK"));
        assertEquals(ThemePreset.SAKURA_PINK, ThemePreset.parse("Sakura Pink"));
        assertEquals(ThemePreset.SAKURA_PINK, ThemePreset.parse("sakura-pink"));
        assertEquals(ThemePreset.MIDNIGHT_KITTY, ThemePreset.parse("midnight kitty"));
        // Unknown input falls back to the house identity rather than failing.
        assertEquals(ThemePreset.FEMBOY, ThemePreset.parse("does-not-exist"));
        assertEquals(ThemePreset.FEMBOY, ThemePreset.parse(null));
    }

    @Test
    void settingsClampOutOfRangeValues() {
        ThemeSettings s = new ThemeSettings();
        s.backgroundOpacity = 5f;
        s.gradientIntensity = -3f;
        s.glowIntensity = 2f;
        s.cornerRadius = 999;
        s.uiScale = 9f;
        s.animationSpeed = -1f;
        s.clamp();
        assertEquals(1f, s.backgroundOpacity);
        assertEquals(0f, s.gradientIntensity);
        assertEquals(1f, s.glowIntensity);
        assertEquals(12, s.cornerRadius);
        assertEquals(1.5f, s.uiScale);
        assertEquals(0f, s.animationSpeed);
    }

    @Test
    void accentOverridesFallBackToPreset() {
        ThemeSettings s = new ThemeSettings();
        s.preset = ThemePreset.COTTON_CANDY;
        assertEquals(ThemePreset.COTTON_CANDY.primary(), s.primary());
        s.setPrimary(0xFF123456);
        assertEquals(0xFF123456, s.primary());
        s.clearOverrides();
        assertEquals(ThemePreset.COTTON_CANDY.primary(), s.primary());
    }

    @Test
    void applyPresetResetsVariantAndOverrides() {
        ThemeSettings s = new ThemeSettings();
        s.variant = ThemeVariant.LIGHT;
        s.setPrimary(0xFF00FF00);
        s.applyPreset(ThemePreset.LAVENDER_DREAM);
        assertEquals(ThemePreset.LAVENDER_DREAM, s.preset);
        assertEquals(ThemeVariant.DARK, s.variant);
        assertNull(s.primaryOverride);
        assertNull(s.secondaryOverride);
    }

    @Test
    void paletteCarriesPresetAccentsAndOpaqueSurfaces() {
        ThemeSettings s = new ThemeSettings();
        s.preset = ThemePreset.MIDNIGHT_KITTY;
        s.variant = ThemeVariant.DARK;
        Palette p = ThemeEngine.build(s);
        assertEquals(ThemePreset.MIDNIGHT_KITTY, p.preset());
        assertEquals(ThemePreset.MIDNIGHT_KITTY.primary(), p.primary());
        assertEquals(ThemePreset.MIDNIGHT_KITTY.secondary(), p.secondary());
        // Background opacity of 0.88 must show through the alpha channel.
        assertTrue(ColorUtil.alpha(p.background()) < 255);
        assertTrue(ColorUtil.alpha(p.background()) > 180);
        // Combat indicators are the spec'd pink/purple/cyan, fully opaque.
        assertEquals(255, ColorUtil.alpha(p.crystalDamage()));
        assertEquals(255, ColorUtil.alpha(p.crystalSelf()));
    }

    @Test
    void lightVariantProducesDarkText() {
        ThemeSettings dark = new ThemeSettings();
        dark.variant = ThemeVariant.DARK;
        ThemeSettings light = new ThemeSettings();
        light.variant = ThemeVariant.LIGHT;
        int darkText = ThemeEngine.build(dark).text();
        int lightText = ThemeEngine.build(light).text();
        assertNotEquals(darkText, lightText);
        assertTrue(ColorUtil.isLight(darkText), "dark variant should use light text");
        assertFalse(ColorUtil.isLight(lightText), "light variant should use dark text");
    }

    @Test
    void themeManagerRebuildsPaletteAndNotifiesListeners() {
        ThemeManager manager = new ThemeManager();
        Palette before = manager.palette();
        Palette[] seen = new Palette[1];
        manager.addListener(palette -> seen[0] = palette);
        manager.applyPreset(ThemePreset.COTTON_CANDY);
        assertNotNull(seen[0], "listener must fire on theme change");
        assertNotEquals(before, seen[0]);
        assertEquals(ThemePreset.COTTON_CANDY, manager.palette().preset());
        assertEquals(ThemePreset.COTTON_CANDY.primary(), manager.palette().primary());
    }

    @Test
    void applyPresetByNameAcceptsLabels() {
        ThemeManager manager = new ThemeManager();
        assertTrue(manager.applyPreset("Lavender Dream"));
        assertEquals(ThemePreset.LAVENDER_DREAM, manager.palette().preset());
        assertFalse(manager.applyPreset("not a theme"));
        // A failed lookup must not change the active theme.
        assertEquals(ThemePreset.LAVENDER_DREAM, manager.palette().preset());
    }

    @Test
    void glowIntensityScalesGlowAlpha() {
        ThemeSettings none = new ThemeSettings();
        none.glowIntensity = 0f;
        ThemeSettings full = new ThemeSettings();
        full.glowIntensity = 1f;
        assertEquals(0, ColorUtil.alpha(ThemeEngine.build(none).glow()));
        assertTrue(ColorUtil.alpha(ThemeEngine.build(full).glow()) > 0);
    }

    @Test
    void cornerRadiusScalesDesignRadii() {
        ThemeSettings squared = new ThemeSettings();
        squared.cornerRadius = 0;
        Palette flat = ThemeEngine.build(squared);
        assertEquals(0, flat.cornerRadius());
        assertEquals(0, flat.radius(6), "radius 0 must square every corner");

        ThemeSettings baseline = new ThemeSettings();
        baseline.cornerRadius = 6;
        Palette normal = ThemeEngine.build(baseline);
        assertEquals(6, normal.radius(6), "the design baseline must leave the radius unchanged");

        ThemeSettings round = new ThemeSettings();
        round.cornerRadius = 12;
        Palette roundest = ThemeEngine.build(round);
        assertEquals(12, roundest.radius(6), "the maximum setting must double the radius");
    }
}
