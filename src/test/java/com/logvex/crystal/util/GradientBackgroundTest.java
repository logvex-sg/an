package com.logvex.crystal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeEngine;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeSettings;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Tests for {@link GradientBackground}: the drift maths and its freeze conditions. */
class GradientBackgroundTest {

    private static Palette palette() {
        ThemeSettings s = new ThemeSettings();
        s.preset = ThemePreset.COTTON_CANDY;
        return ThemeEngine.build(s);
    }

    @Test
    void reducedMotionUsesTheBaseColoursExactly() {
        Palette p = palette();
        GradientBackground bg = new GradientBackground();
        bg.update(p, 1.5f, true);
        assertEquals(p.backgroundTop(), bg.topColor());
        assertEquals(p.backgroundBottom(), bg.bottomColor());
    }

    @Test
    void zeroAnimationSpeedUsesTheBaseColours() {
        Palette p = palette();
        GradientBackground bg = new GradientBackground();
        bg.update(p, 0f, false);
        assertEquals(p.backgroundTop(), bg.topColor());
        assertEquals(p.backgroundBottom(), bg.bottomColor());
    }

    @Test
    void bothEndsSwingInBothDirections() {
        Palette p = palette();
        AtomicLong clock = new AtomicLong();
        GradientBackground bg = new GradientBackground(clock::get);

        boolean topBrighter = false;
        boolean topDarker = false;
        boolean bottomBrighter = false;
        boolean bottomDarker = false;
        int topBase = ColorUtil.luminance(p.backgroundTop());
        int bottomBase = ColorUtil.luminance(p.backgroundBottom());
        for (int i = 0; i < 40; i++) {
            clock.set(i * 1_000_000_000L);
            bg.update(p, 1f, false);
            int dTop = Integer.signum(ColorUtil.luminance(bg.topColor()) - topBase);
            int dBottom = Integer.signum(ColorUtil.luminance(bg.bottomColor()) - bottomBase);
            topBrighter |= dTop > 0;
            topDarker |= dTop < 0;
            bottomBrighter |= dBottom > 0;
            bottomDarker |= dBottom < 0;
        }
        assertTrue(topBrighter && topDarker, "the top end should swing both ways");
        assertTrue(bottomBrighter && bottomDarker, "the bottom end should swing both ways");
    }

    @Test
    void theTwoEndsAreAlwaysOutOfPhase() {
        Palette p = palette();
        AtomicLong clock = new AtomicLong();
        GradientBackground bg = new GradientBackground(clock::get);
        int topBase = ColorUtil.luminance(p.backgroundTop());
        int bottomBase = ColorUtil.luminance(p.backgroundBottom());
        for (int i = 0; i < 40; i++) {
            clock.set(i * 500_000_000L);
            bg.update(p, 1f, false);
            int dTop = Integer.signum(ColorUtil.luminance(bg.topColor()) - topBase);
            int dBottom = Integer.signum(ColorUtil.luminance(bg.bottomColor()) - bottomBase);
            // The two ends must never move the same way: when one brightens the other darkens.
            assertTrue(!(dTop > 0 && dBottom > 0), "ends brightened together at step " + i);
            assertTrue(!(dTop < 0 && dBottom < 0), "ends darkened together at step " + i);
        }
    }

    @Test
    void animatedOutputDiffersFromTheFrozenBaseline() {
        Palette p = palette();
        AtomicLong clock = new AtomicLong();
        GradientBackground animated = new GradientBackground(clock::get);
        GradientBackground frozen = new GradientBackground();
        frozen.update(p, 0f, false);
        boolean differs = false;
        for (int i = 0; i < 40 && !differs; i++) {
            clock.set(i * 500_000_000L);
            animated.update(p, 2f, false);
            differs = animated.topColor() != frozen.topColor()
                    || animated.bottomColor() != frozen.bottomColor();
        }
        assertNotEquals(false, differs, "animation speed 2 should eventually move the gradient");
    }
}
