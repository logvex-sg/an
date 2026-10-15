package com.logvex.crystal.theme;

/**
 * A light or dark pastel variant. The variant adjusts how far panel and background
 * colours sit from pure black, so the same accent palette reads well on either.
 */
public enum ThemeVariant {

    /** Deep midnight surfaces; the default look. */
    DARK("Dark", 0.0f),
    /** Lifted, milkier pastel surfaces with the same accents. */
    LIGHT("Light", 0.55f);

    private final String label;
    private final float lift;

    ThemeVariant(String label, float lift) {
        this.label = label;
        this.lift = lift;
    }

    public String label() {
        return label;
    }

    /** How far surfaces are lightened toward the pastel tint (0..1). */
    public float lift() {
        return lift;
    }

    public static ThemeVariant parse(String value) {
        if (value != null) {
            for (ThemeVariant variant : values()) {
                if (variant.name().equalsIgnoreCase(value.trim()) || variant.label.equalsIgnoreCase(value.trim())) {
                    return variant;
                }
            }
        }
        return DARK;
    }
}
