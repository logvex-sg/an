package com.logvex.crystal.util;

import com.logvex.crystal.theme.Palette;
import java.util.function.LongSupplier;

/**
 * Computes the slowly-drifting pastel backdrop for the interface: a vertical gradient
 * between the palette's top and bottom colours, with the two ends' relative brightness
 * oscillating out of phase so the wash gently breathes rather than sitting static.
 *
 * <p>Kept free of any rendering or Minecraft types so the maths is unit-testable; the
 * caller only reads {@link #topColor()}/{@link #bottomColor()}. The drift is driven by the
 * theme's animation speed and collapses to a constant when animation is off or
 * reduced-motion is set, so it can never cost frames during combat.
 *
 * <p>The clock is injectable so a test can advance time deterministically instead of
 * sleeping.
 */
public final class GradientBackground {

    /** Radians of drift per second at animation speed 1 (a ~20 second breathing period). */
    private static final float DRIFT_RADIANS_PER_SECOND = 0.314f;
    /** Peak lighten/darken of each end, as a fraction; deliberately subtle. */
    private static final float DRIFT_AMOUNT = 0.12f;

    private final LongSupplier clockNanos;
    private final long startNanos;
    private int topColor;
    private int bottomColor;

    public GradientBackground() {
        this(System::nanoTime);
    }

    /** Test/advanced constructor: pass a controllable nanosecond clock. */
    public GradientBackground(LongSupplier clockNanos) {
        this.clockNanos = clockNanos;
        this.startNanos = clockNanos.getAsLong();
    }

    /**
     * Recomputes the two end colours.
     *
     * @param palette     the active palette (supplies the base top/bottom colours)
     * @param animationSpeed theme animation-speed multiplier; 0 freezes the drift
     * @param reducedMotion when true the drift is frozen regardless of speed
     */
    public void update(Palette palette, float animationSpeed, boolean reducedMotion) {
        int baseTop = palette.backgroundTop();
        int baseBottom = palette.backgroundBottom();
        if (reducedMotion || animationSpeed <= 0f) {
            topColor = baseTop;
            bottomColor = baseBottom;
            return;
        }
        float seconds = (clockNanos.getAsLong() - startNanos) / 1_000_000_000f;
        float phase = seconds * animationSpeed * DRIFT_RADIANS_PER_SECOND;
        // Two ends move opposite each other, so the gradient subtly slides.
        float topAmount = DRIFT_AMOUNT * (float) Math.sin(phase);
        float bottomAmount = DRIFT_AMOUNT * (float) Math.sin(phase + Math.PI);
        topColor = blend(baseTop, topAmount);
        bottomColor = blend(baseBottom, bottomAmount);
    }

    public int topColor() {
        return topColor;
    }

    public int bottomColor() {
        return bottomColor;
    }

    /** Lightens (positive) or darkens (negative) an opaque colour by up to {@code amount}. */
    private static int blend(int argb, float amount) {
        return amount >= 0f
                ? ColorUtil.lighten(argb, amount)
                : ColorUtil.darken(argb, -amount);
    }
}
