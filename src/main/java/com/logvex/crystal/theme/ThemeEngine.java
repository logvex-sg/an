package com.logvex.crystal.theme;

import com.logvex.crystal.util.ColorUtil;

/**
 * Turns {@link ThemeSettings} into an immutable {@link Palette}.
 *
 * <p>All derivation lives here so the rules are in one place and unit-testable: the
 * preset supplies anchor colours, the variant decides how far surfaces sit from black,
 * and opacity/gradient/glow settings scale the derived values. The engine never mutates
 * the settings; it reads them and returns a new palette.
 */
public final class ThemeEngine {

    private ThemeEngine() {
    }

    /** Builds a palette for the given settings. The settings are not modified. */
    public static Palette build(ThemeSettings settings) {
        ThemeSettings s = settings.copy();
        s.clamp();

        Palette.Builder b = new Palette.Builder();
        b.preset = s.preset;
        b.variant = s.variant;

        float lift = s.variant.lift();
        int bgBase = s.preset.background();
        int surfaceBase = s.preset.surface();

        // Surfaces: lift toward the pastel tint for the light variant, otherwise deepen.
        int background = blendSurface(bgBase, s, lift, 0.0f);
        int panel = blendSurface(surfaceBase, s, lift, 0.04f);
        int panelAlt = blendSurface(surfaceBase, s, lift, 0.10f);
        int panelHover = blendSurface(surfaceBase, s, lift, 0.18f);
        int card = blendSurface(surfaceBase, s, lift, 0.07f);

        b.background = ColorUtil.withAlpha(background, Math.round(255 * s.backgroundOpacity));
        b.backgroundTop = ColorUtil.withAlpha(ColorUtil.lerp(background, s.primary(), 0.14f * s.gradientIntensity),
                Math.round(255 * s.backgroundOpacity));
        b.backgroundBottom = ColorUtil.withAlpha(
                ColorUtil.lerp(background, s.preset.tertiary(), 0.22f * s.gradientIntensity),
                Math.round(255 * s.backgroundOpacity));
        b.panel = ColorUtil.withAlpha(panel, Math.round(255 * Math.min(1f, s.backgroundOpacity + 0.06f)));
        b.panelAlt = ColorUtil.withAlpha(panelAlt, Math.round(255 * Math.min(1f, s.backgroundOpacity + 0.08f)));
        b.panelHover = ColorUtil.withAlpha(panelHover, Math.round(255 * Math.min(1f, s.backgroundOpacity + 0.10f)));
        b.card = ColorUtil.withAlpha(card, Math.round(255 * Math.min(1f, s.backgroundOpacity + 0.05f)));

        b.primary = s.primary();
        b.secondary = s.secondary();
        b.tertiary = s.preset.tertiary();

        // Accent gradient runs primary -> secondary, brightened a touch for the glow.
        b.accentGradientStart = ColorUtil.withAlpha(b.primary, 255);
        b.accentGradientEnd = ColorUtil.withAlpha(b.secondary, 255);

        b.border = ColorUtil.withAlpha(ColorUtil.lerp(b.primary, 0xFFFFFFFF, 0.10f), 190);
        b.borderSoft = ColorUtil.withAlpha(b.tertiary, 110);

        boolean light = s.variant == ThemeVariant.LIGHT;
        b.text = light ? ColorUtil.withAlpha(0xFF241626, 255) : s.preset.text();
        b.textDim = light ? ColorUtil.withAlpha(0xFF6B5570, 255) : ColorUtil.withAlpha(ColorUtil.lerp(s.preset.text(), 0xFF888888, 0.45f), 220);
        b.textOnAccent = ColorUtil.contrasting(b.primary, 0xFF1A1020, 0xFFFFFFFF);

        // Combat/state indicators. Crystal colours are the spec'd pink/purple/cyan.
        b.enabled = ColorUtil.withAlpha(b.primary, 255);
        b.disabled = ColorUtil.withAlpha(light ? 0xFFB8A8C0 : 0xFF6A6278, 255);
        b.warning = ColorUtil.withAlpha(0xFFFFC46B, 255);
        b.danger = ColorUtil.withAlpha(0xFFFF6B85, 255);
        b.crystalDamage = ColorUtil.withAlpha(0xFFFF7FB0, 255);
        b.crystalSelf = ColorUtil.withAlpha(0xFF8FE3FF, 255);
        b.health = ColorUtil.withAlpha(0xFFFF9EC4, 255);
        b.armor = ColorUtil.withAlpha(0xFFA0D8FF, 255);
        b.glow = ColorUtil.glow(b.primary, s.glowIntensity);
        b.cornerRadius = s.cornerRadius;
        return new Palette(b);
    }

    /**
     * Derives a surface colour from a base. For the dark variant the base is left as-is
     * (optionally deepened); for the light variant it is mixed toward the pastel accent
     * so the surface reads as a milky pastel rather than a dark panel.
     */
    private static int blendSurface(int base, ThemeSettings s, float lift, float deepen) {
        if (lift > 0f) {
            int tint = ColorUtil.lerp(s.primary(), 0xFFFFFFFF, 0.5f);
            int milky = ColorUtil.lerp(base, tint, 0.72f * lift + 0.35f * lift);
            return ColorUtil.withAlpha(milky, 255);
        }
        return ColorUtil.withAlpha(ColorUtil.darken(base, deepen), 255);
    }
}
