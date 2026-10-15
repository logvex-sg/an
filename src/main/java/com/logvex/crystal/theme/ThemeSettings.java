package com.logvex.crystal.theme;

import com.logvex.crystal.util.ColorUtil;

/**
 * Every user-tunable appearance knob, as plain fields with clamped accessors.
 *
 * <p>This is deliberately a dumb data holder with no Minecraft or file dependencies, so
 * it can be round-tripped through JSON and unit tested on its own. The client holds a
 * single instance, mutates it from the ClickGUI/commands, and asks the
 * {@link ThemeEngine} to turn it into a {@link Palette} whenever it changes.
 */
public final class ThemeSettings {

    /** Which built-in identity to start from. */
    public ThemePreset preset = ThemePreset.FEMBOY;
    /** Light or dark pastel variant. */
    public ThemeVariant variant = ThemeVariant.DARK;

    /** Accent overrides; {@code null} means "use the preset's colour". */
    public Integer primaryOverride;
    public Integer secondaryOverride;

    /** Opacity of panels/backgrounds, 0..1 (1 = fully opaque). */
    public float backgroundOpacity = 0.88f;
    /** How strong the animated background gradient is, 0..1. */
    public float gradientIntensity = 0.65f;
    /** How strong glow/border halos are, 0..1. */
    public float glowIntensity = 0.55f;
    /** Corner rounding in pixels, 0..12. */
    public int cornerRadius = 6;
    /** Interface scale multiplier, 0.75..1.5. */
    public float uiScale = 1.0f;
    /** Animation speed multiplier, 0..2 (0 freezes animation). */
    public float animationSpeed = 1.0f;

    /** Draw cat ears, stars, hearts, paw prints and other motifs. */
    public boolean decorations = true;
    /** Cute cat-themed notifications, sounds and cursor accents. */
    public boolean catCosmetics = true;
    /** Respect a reduced-motion preference: disable/shorten animations. */
    public boolean reducedMotion = false;
    /** Blur the world behind screens where the platform supports it. */
    public boolean blur = true;
    /** Draw the custom pastel cursor. */
    public boolean customCursor = true;
    /** Play soft UI sounds on click/toggle. */
    public boolean guiSounds = true;
    /** Show the cute loading splash text. */
    public boolean splashText = true;
    /** Enable the hidden cosmetic easter egg. */
    public boolean easterEgg = false;

    /** Effective primary accent. */
    public int primary() {
        return primaryOverride != null ? primaryOverride : preset.primary();
    }

    /** Effective secondary accent. */
    public int secondary() {
        return secondaryOverride != null ? secondaryOverride : preset.secondary();
    }

    public void setPrimary(int argb) {
        this.primaryOverride = argb;
    }

    public void setSecondary(int argb) {
        this.secondaryOverride = argb;
    }

    /** Clears accent overrides so the preset's own colours are used again. */
    public void clearOverrides() {
        this.primaryOverride = null;
        this.secondaryOverride = null;
    }

    /**
     * Applies a preset and, unless it is {@link ThemePreset#DEFAULT}, restores the house
     * pastel variant. Accent overrides are cleared so the preset is shown faithfully.
     */
    public void applyPreset(ThemePreset target) {
        this.preset = target;
        this.variant = ThemeVariant.DARK;
        clearOverrides();
    }

    public void clamp() {
        backgroundOpacity = clamp01(backgroundOpacity);
        gradientIntensity = clamp01(gradientIntensity);
        glowIntensity = clamp01(glowIntensity);
        cornerRadius = Math.max(0, Math.min(12, cornerRadius));
        uiScale = Math.max(0.75f, Math.min(1.5f, uiScale));
        animationSpeed = Math.max(0f, Math.min(2f, animationSpeed));
        if (primaryOverride != null) {
            primaryOverride = ColorUtil.withAlpha(primaryOverride, 255);
        }
        if (secondaryOverride != null) {
            secondaryOverride = ColorUtil.withAlpha(secondaryOverride, 255);
        }
    }

    public ThemeSettings copy() {
        ThemeSettings copy = new ThemeSettings();
        copy.preset = preset;
        copy.variant = variant;
        copy.primaryOverride = primaryOverride;
        copy.secondaryOverride = secondaryOverride;
        copy.backgroundOpacity = backgroundOpacity;
        copy.gradientIntensity = gradientIntensity;
        copy.glowIntensity = glowIntensity;
        copy.cornerRadius = cornerRadius;
        copy.uiScale = uiScale;
        copy.animationSpeed = animationSpeed;
        copy.decorations = decorations;
        copy.catCosmetics = catCosmetics;
        copy.reducedMotion = reducedMotion;
        copy.blur = blur;
        copy.customCursor = customCursor;
        copy.guiSounds = guiSounds;
        copy.splashText = splashText;
        copy.easterEgg = easterEgg;
        return copy;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
