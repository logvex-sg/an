package com.logvex.crystal.util;

/**
 * Pure ARGB colour maths shared by the theme engine, the ClickGUI and the HUD.
 *
 * <p>Colours are packed {@code 0xAARRGGBB} ints, the same format {@code DrawContext}
 * consumes, so a value computed here can be handed straight to the renderer. Every
 * method is side-effect free and independent of Minecraft, which is what lets the
 * whole theme layer be unit tested without a game runtime.
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    public static int of(int r, int g, int b, int a) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(int r, int g, int b) {
        return of(r, g, b, 255);
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** Replaces the alpha channel, keeping the colour. */
    public static int withAlpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | (clamp(a) << 24);
    }

    /** Scales the existing alpha by {@code factor} (0..1). */
    public static int scaleAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    /** Linear interpolation in straight (non-premultiplied) ARGB. */
    public static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(alpha(from) * (1 - t) + alpha(to) * t);
        int r = Math.round(red(from) * (1 - t) + red(to) * t);
        int g = Math.round(green(from) * (1 - t) + green(to) * t);
        int b = Math.round(blue(from) * (1 - t) + blue(to) * t);
        return of(r, g, b, a);
    }

    /** Mixes two colours by {@code t}, forcing full opacity on the result. */
    public static int mixOpaque(int from, int to, float t) {
        return withAlpha(lerp(from, to, t), 255);
    }

    public static int darken(int argb, float amount) {
        return of(Math.round(red(argb) * (1 - amount)),
                Math.round(green(argb) * (1 - amount)),
                Math.round(blue(argb) * (1 - amount)),
                alpha(argb));
    }

    public static int lighten(int argb, float amount) {
        return of(Math.round(red(argb) + (255 - red(argb)) * amount),
                Math.round(green(argb) + (255 - green(argb)) * amount),
                Math.round(blue(argb) + (255 - blue(argb)) * amount),
                alpha(argb));
    }

    /** A soft glow colour: the hue pushed toward white and given a low alpha. */
    public static int glow(int argb, float intensity) {
        int base = lighten(argb, 0.45f);
        return withAlpha(base, Math.round(70 * Math.max(0f, Math.min(1f, intensity))));
    }

    /**
     * Perceived luminance (0..255) using Rec. 601 weights. Used to pick a readable
     * foreground over an arbitrary background, e.g. the light/dark pastel variants.
     */
    public static int luminance(int argb) {
        return (int) (0.299 * red(argb) + 0.587 * green(argb) + 0.114 * blue(argb));
    }

    public static boolean isLight(int argb) {
        return luminance(argb) > 140;
    }

    /** Chooses between a light and dark foreground for maximum contrast on {@code background}. */
    public static int contrasting(int background, int light, int dark) {
        return isLight(background) ? dark : light;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
