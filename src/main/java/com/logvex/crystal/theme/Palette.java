package com.logvex.crystal.theme;

import com.logvex.crystal.util.ColorUtil;

/**
 * An immutable, fully-resolved colour palette.
 *
 * <p>The {@link ThemeEngine} expands a {@link ThemePreset} plus the user's accent,
 * opacity, gradient and glow settings into one of these. Every screen and HUD element
 * reads its colours from here, so a theme change is a single object swap rather than a
 * hunt through hard-coded constants. Immutability means the render thread can read a
 * palette without synchronisation while the client thread builds a new one.
 */
public final class Palette {

    private final ThemePreset preset;
    private final ThemeVariant variant;

    private final int background;
    private final int backgroundTop;
    private final int backgroundBottom;
    private final int panel;
    private final int panelAlt;
    private final int panelHover;
    private final int card;
    private final int border;
    private final int borderSoft;
    private final int primary;
    private final int secondary;
    private final int tertiary;
    private final int accentGradientStart;
    private final int accentGradientEnd;
    private final int text;
    private final int textDim;
    private final int textOnAccent;
    private final int enabled;
    private final int disabled;
    private final int warning;
    private final int danger;
    private final int crystalDamage;
    private final int crystalSelf;
    private final int health;
    private final int armor;
    private final int glow;
    private final int cornerRadius;

    Palette(Builder builder) {
        this.preset = builder.preset;
        this.variant = builder.variant;
        this.background = builder.background;
        this.backgroundTop = builder.backgroundTop;
        this.backgroundBottom = builder.backgroundBottom;
        this.panel = builder.panel;
        this.panelAlt = builder.panelAlt;
        this.panelHover = builder.panelHover;
        this.card = builder.card;
        this.border = builder.border;
        this.borderSoft = builder.borderSoft;
        this.primary = builder.primary;
        this.secondary = builder.secondary;
        this.tertiary = builder.tertiary;
        this.accentGradientStart = builder.accentGradientStart;
        this.accentGradientEnd = builder.accentGradientEnd;
        this.text = builder.text;
        this.textDim = builder.textDim;
        this.textOnAccent = builder.textOnAccent;
        this.enabled = builder.enabled;
        this.disabled = builder.disabled;
        this.warning = builder.warning;
        this.danger = builder.danger;
        this.crystalDamage = builder.crystalDamage;
        this.crystalSelf = builder.crystalSelf;
        this.health = builder.health;
        this.armor = builder.armor;
        this.glow = builder.glow;
        this.cornerRadius = builder.cornerRadius;
    }

    public ThemePreset preset() {
        return preset;
    }

    public ThemeVariant variant() {
        return variant;
    }

    public int background() {
        return background;
    }

    public int backgroundTop() {
        return backgroundTop;
    }

    public int backgroundBottom() {
        return backgroundBottom;
    }

    public int panel() {
        return panel;
    }

    public int panelAlt() {
        return panelAlt;
    }

    public int panelHover() {
        return panelHover;
    }

    public int card() {
        return card;
    }

    public int border() {
        return border;
    }

    public int borderSoft() {
        return borderSoft;
    }

    public int primary() {
        return primary;
    }

    public int secondary() {
        return secondary;
    }

    public int tertiary() {
        return tertiary;
    }

    public int accentGradientStart() {
        return accentGradientStart;
    }

    public int accentGradientEnd() {
        return accentGradientEnd;
    }

    public int text() {
        return text;
    }

    public int textDim() {
        return textDim;
    }

    public int textOnAccent() {
        return textOnAccent;
    }

    public int enabled() {
        return enabled;
    }

    public int disabled() {
        return disabled;
    }

    public int warning() {
        return warning;
    }

    public int danger() {
        return danger;
    }

    public int crystalDamage() {
        return crystalDamage;
    }

    public int crystalSelf() {
        return crystalSelf;
    }

    public int health() {
        return health;
    }

    public int armor() {
        return armor;
    }

    /** The glow tint used for animated borders and soft shadows. */
    public int glow() {
        return glow;
    }

    /**
     * The user's corner-radius setting (0..12). Panels round by {@link #radius(int)} times
     * this value, so one slider reshapes every rounded surface in the interface.
     */
    public int cornerRadius() {
        return cornerRadius;
    }

    /**
     * Scales a design-time corner radius by the user's setting. A setting of 6 (the design
     * baseline) leaves the radius unchanged; 0 squares every corner; 12 doubles it.
     */
    public int radius(int base) {
        if (base <= 0 || cornerRadius <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * (cornerRadius / 6f)));
    }

    /** A readable foreground for text drawn on top of {@link #primary}. */
    public int onPrimary() {
        return ColorUtil.contrasting(primary, 0xFF1A1020, 0xFFFFFFFF);
    }

    /** Mutable builder used only by the theme engine. */
    static final class Builder {
        ThemePreset preset;
        ThemeVariant variant;
        int background;
        int backgroundTop;
        int backgroundBottom;
        int panel;
        int panelAlt;
        int panelHover;
        int card;
        int border;
        int borderSoft;
        int primary;
        int secondary;
        int tertiary;
        int accentGradientStart;
        int accentGradientEnd;
        int text;
        int textDim;
        int textOnAccent;
        int enabled;
        int disabled;
        int warning;
        int danger;
        int crystalDamage;
        int crystalSelf;
        int health;
        int armor;
        int glow;
        int cornerRadius;
    }
}
