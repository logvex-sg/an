package com.logvex.crystal.cosmetic;

/**
 * Shared state for the purely cosmetic player accessories (cat ears, bow, halo).
 *
 * <p>This is the single source of truth the 3D feature renderer reads while drawing other
 * players. It holds only plain fields and pure functions, so it has no Minecraft dependency
 * and can be unit tested on its own. The owning module writes {@link #setEnabled},
 * {@link #setStyle} and friends; the client mirrors the live theme gate (decorations, cat
 * cosmetics, reduced motion) into {@link #setCuteGate}. Nothing here ever touches gameplay.
 *
 * <p>All fields are {@code volatile}: the client thread mutates them, the render thread
 * reads them without locking.
 */
public final class PlayerCosmetics {

    /** The accessory styles the renderer can draw. */
    public enum Style {
        /** Fluffy cat ears. */
        CAT_EARS("Cat Ears"),
        /** Cat ears with a pastel bow. */
        CAT_EARS_BOW("Cat Ears + Bow"),
        /** A floating pastel halo. */
        HALO("Halo"),
        /** Nothing drawn. */
        NONE("None");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static volatile boolean enabled;
    private static volatile boolean animate = true;
    private static volatile float glow = 0.5f;
    private static volatile Style style = Style.CAT_EARS;
    /** Live theme gate: decorations and cat cosmetics on, reduced motion off. */
    private static volatile boolean cuteGate;

    private PlayerCosmetics() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setAnimate(boolean value) {
        animate = value;
    }

    public static void setGlow(float value) {
        glow = Math.max(0f, Math.min(1f, value));
    }

    public static float glow() {
        return glow;
    }

    public static void setStyle(Style value) {
        style = value == null ? Style.NONE : value;
    }

    public static Style style() {
        return style;
    }

    /** Mirrors the theme's cosmetic gate so the renderer needs no theme access. */
    public static void setCuteGate(boolean value) {
        cuteGate = value;
    }

    public static boolean cuteGate() {
        return cuteGate;
    }

    /** True when the accessories should be drawn at all this frame. */
    public static boolean visible() {
        return enabled && cuteGate && style != Style.NONE;
    }

    /** True when the subtle idle animation should run (never during reduced motion). */
    public static boolean animated() {
        return visible() && animate;
    }

    /**
     * A gentle sine sway for the ears, in radians, derived from the entity's age.
     * Pure so it can be tested; amplitude scales with the glow setting so a brighter
     * cosmetic also reads as a livelier one. One full cycle spans {@value #SWAY_PERIOD_TICKS}
     * ticks (two seconds at 20 TPS), slow enough to never distract during combat.
     */
    public static final float SWAY_PERIOD_TICKS = 40f;

    public static float sway(float ageTicks, float phase) {
        float amplitude = 0.05f + 0.05f * glow;
        return amplitude * (float) Math.sin(ageTicks * (float) (Math.PI * 2.0 / SWAY_PERIOD_TICKS) + phase);
    }

    /** Alpha (0..1) applied to the glow/emissive overlay; scales with the glow setting. */
    public static float overlayAlpha() {
        return 0.25f + 0.5f * glow;
    }
}
