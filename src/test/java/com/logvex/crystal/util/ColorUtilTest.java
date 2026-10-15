package com.logvex.crystal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the ARGB maths the theme engine and renderer depend on. */
class ColorUtilTest {

    @Test
    void packsAndUnpacksChannels() {
        int color = ColorUtil.of(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x78, ColorUtil.alpha(color));
        assertEquals(0x12, ColorUtil.red(color));
        assertEquals(0x34, ColorUtil.green(color));
        assertEquals(0x56, ColorUtil.blue(color));
    }

    @Test
    void channelsAreClamped() {
        int color = ColorUtil.of(300, -20, 999, 400);
        assertEquals(255, ColorUtil.red(color));
        assertEquals(0, ColorUtil.green(color));
        assertEquals(255, ColorUtil.blue(color));
        assertEquals(255, ColorUtil.alpha(color));
    }

    @Test
    void withAlphaReplacesOnlyAlpha() {
        int color = ColorUtil.rgb(10, 20, 30);
        int faded = ColorUtil.withAlpha(color, 100);
        assertEquals(100, ColorUtil.alpha(faded));
        assertEquals(10, ColorUtil.red(faded));
        assertEquals(20, ColorUtil.green(faded));
        assertEquals(30, ColorUtil.blue(faded));
    }

    @Test
    void lerpInterpolatesBothEndsAndMidpoint() {
        int black = ColorUtil.rgb(0, 0, 0);
        int white = ColorUtil.rgb(255, 255, 255);
        assertEquals(0, ColorUtil.red(ColorUtil.lerp(black, white, 0f)));
        assertEquals(255, ColorUtil.red(ColorUtil.lerp(black, white, 1f)));
        assertEquals(128, ColorUtil.red(ColorUtil.lerp(black, white, 0.5f)));
        // Out-of-range t is clamped, not extrapolated.
        assertEquals(255, ColorUtil.red(ColorUtil.lerp(black, white, 5f)));
    }

    @Test
    void scaleAlphaLeavesRgbUntouched() {
        int color = ColorUtil.of(200, 100, 50, 200);
        int scaled = ColorUtil.scaleAlpha(color, 0.5f);
        assertEquals(100, ColorUtil.alpha(scaled));
        assertEquals(200, ColorUtil.red(scaled));
    }

    @Test
    void luminanceAndContrastPickReadableForeground() {
        int light = ColorUtil.rgb(240, 240, 240);
        int dark = ColorUtil.rgb(20, 20, 20);
        assertTrue(ColorUtil.isLight(light));
        assertFalse(ColorUtil.isLight(dark));
        // contrasting(background, lightForeground, darkForeground)
        assertEquals(dark, ColorUtil.contrasting(light, light, dark));
        assertEquals(light, ColorUtil.contrasting(dark, light, dark));
    }

    @Test
    void glowIsTranslucentAndBrighterThanSource() {
        int source = ColorUtil.rgb(255, 100, 180);
        int glow = ColorUtil.glow(source, 1f);
        assertTrue(ColorUtil.alpha(glow) < 255);
        assertTrue(ColorUtil.luminance(glow) > ColorUtil.luminance(source));
        assertEquals(0, ColorUtil.alpha(ColorUtil.glow(source, 0f)));
    }

    @Test
    void hsvHitsThePrimaryHues() {
        assertEquals(255, ColorUtil.red(ColorUtil.hsv(0f, 1f, 1f)));
        assertEquals(255, ColorUtil.green(ColorUtil.hsv(120f, 1f, 1f)));
        assertEquals(255, ColorUtil.blue(ColorUtil.hsv(240f, 1f, 1f)));
        // Zero saturation is greyscale; zero value is black.
        int grey = ColorUtil.hsv(200f, 0f, 1f);
        assertEquals(ColorUtil.red(grey), ColorUtil.blue(grey));
        assertEquals(0, ColorUtil.luminance(ColorUtil.hsv(50f, 1f, 0f)));
    }

    @Test
    void hsvWrapsHueAndAlwaysOpaque() {
        assertEquals(ColorUtil.hsv(10f, 0.5f, 0.8f), ColorUtil.hsv(370f, 0.5f, 0.8f));
        assertEquals(ColorUtil.hsv(10f, 0.5f, 0.8f), ColorUtil.hsv(-350f, 0.5f, 0.8f));
        assertEquals(255, ColorUtil.alpha(ColorUtil.hsv(180f, 0.4f, 0.9f)));
    }
}
