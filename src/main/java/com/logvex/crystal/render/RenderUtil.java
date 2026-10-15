package com.logvex.crystal.render;

import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;

/**
 * The drawing vocabulary the whole interface is built from: rounded panels, soft shadows,
 * gradient fills, glows and decorative glyphs.
 *
 * <p>Vanilla {@code DrawContext} only offers axis-aligned rectangles, so the rounded look
 * is composed from a central rectangle plus corner "notches" that step the corner in by a
 * pixel per row. That keeps everything on the normal 2-D GUI pipeline (no custom shaders,
 * no GL state), which is both portable and cheap enough to run every frame.
 *
 * <p>All methods take the active palette's corner radius and clamp it, so a radius of 0
 * degrades gracefully to plain rectangles.
 */
public final class RenderUtil {

    private RenderUtil() {
    }

    /** Fills a rectangle with rounded corners of the given radius. */
    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = clampRadius(radius, width, height);
        if (r <= 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        context.fill(x + r, y, x + width - r, y + height, color);
        context.fill(x, y + r, x + r, y + height - r, color);
        context.fill(x + width - r, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + r, y + i + 1, color);
            context.fill(x + width - r, y + i, x + width - inset, y + i + 1, color);
            context.fill(x + inset, y + height - i - 1, x + r, y + height - i, color);
            context.fill(x + width - r, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** A rounded rectangle with a 1px border drawn just inside the edge. */
    public static void roundedPanel(DrawContext context, int x, int y, int width, int height,
                                    int radius, int fill, int border) {
        roundedRect(context, x, y, width, height, radius, fill);
        roundedOutline(context, x, y, width, height, radius, border);
    }

    /** Draws a 1px rounded outline. */
    public static void roundedOutline(DrawContext context, int x, int y, int width, int height,
                                      int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = clampRadius(radius, width, height);
        if (r <= 0) {
            context.fill(x, y, x + width, y + 1, color);
            context.fill(x, y + height - 1, x + width, y + height, color);
            context.fill(x, y, x + 1, y + height, color);
            context.fill(x + width - 1, y, x + width, y + height, color);
            return;
        }
        context.fill(x + r, y, x + width - r, y + 1, color);
        context.fill(x + r, y + height - 1, x + width - r, y + height, color);
        context.fill(x, y + r, x + 1, y + height - r, color);
        context.fill(x + width - 1, y + r, x + width, y + height - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            context.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            context.fill(x + width - inset - 1, y + i, x + width - inset, y + i + 1, color);
            context.fill(x + inset, y + height - i - 1, x + inset + 1, y + height - i, color);
            context.fill(x + width - inset - 1, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    /** A soft drop shadow beneath a rounded panel, drawn as stacked translucent rectangles. */
    public static void softShadow(DrawContext context, int x, int y, int width, int height,
                                  int radius, int strength) {
        int layers = 4;
        for (int i = layers; i >= 1; i--) {
            int alpha = (int) (strength * (1f - (float) i / (layers + 1)) / layers * 3f);
            int color = ColorUtil.of(20, 8, 26, Math.max(0, Math.min(90, alpha)));
            roundedRect(context, x - i, y + i, width + i * 2, height + i, radius + i, color);
        }
    }

    /** A vertical gradient fill between two colours. */
    public static void verticalGradient(DrawContext context, int x, int y, int width, int height,
                                        int top, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        context.fillGradient(x, y, x + width, y + height, top, bottom);
    }

    /** A horizontal gradient fill between two colours, composed column by column. */
    public static void horizontalGradient(DrawContext context, int x, int y, int width, int height,
                                          int left, int right) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int i = 0; i < width; i++) {
            int color = ColorUtil.lerp(left, right, (float) i / width);
            context.fill(x + i, y, x + i + 1, y + height, color);
        }
    }

    /** A rounded panel filled with a vertical gradient plus a matching outline. */
    public static void roundedGradientPanel(DrawContext context, int x, int y, int width, int height,
                                            int radius, int top, int bottom, int border) {
        roundedRect(context, x, y, width, height, radius, top);
        // Overlay a vertical gradient clipped to the rounded shape by drawing it row by row.
        int r = clampRadius(radius, width, height);
        for (int row = 0; row < height; row++) {
            int inset = rowInset(row, height, r);
            int color = ColorUtil.lerp(top, bottom, (float) row / Math.max(1, height));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
        roundedOutline(context, x, y, width, height, radius, border);
    }

    /** A soft glow ring drawn just outside a rounded rectangle. */
    public static void glow(DrawContext context, int x, int y, int width, int height, int radius, int glowColor) {
        for (int i = 3; i >= 1; i--) {
            int color = ColorUtil.scaleAlpha(glowColor, 0.28f / i);
            roundedOutline(context, x - i, y - i, width + i * 2, height + i * 2, radius + i, color);
        }
    }

    /** A small filled circle (for status dots, toggle knobs and bullet points). */
    public static void dot(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            context.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    /** A pixel-art heart glyph, built from a small mask so it reads at any GUI scale. */
    public static void heart(DrawContext context, int x, int y, int size, int color) {
        int[][] mask = {
                {0, 1, 1, 0, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1},
                {0, 1, 1, 1, 1, 1, 0},
                {0, 0, 1, 1, 1, 0, 0},
                {0, 0, 0, 1, 0, 0, 0}
        };
        drawMask(context, x, y, size, color, mask);
    }

    /** A pixel-art paw print, the theme's signature decorative motif. */
    public static void paw(DrawContext context, int x, int y, int size, int color) {
        int[][] mask = {
                {0, 1, 0, 0, 0, 1, 0},
                {1, 1, 1, 0, 1, 1, 1},
                {0, 1, 0, 0, 0, 1, 0},
                {0, 0, 1, 1, 1, 0, 0},
                {0, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 0},
                {0, 0, 1, 1, 1, 0, 0}
        };
        drawMask(context, x, y, size, color, mask);
    }

    /** A small four-point sparkle/star. */
    public static void star(DrawContext context, int cx, int cy, int radius, int color) {
        context.fill(cx, cy - radius, cx + 1, cy + radius + 1, color);
        context.fill(cx - radius, cy, cx + radius + 1, cy + 1, color);
        for (int i = 1; i <= radius; i++) {
            int a = ColorUtil.scaleAlpha(color, 0.6f);
            context.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, a);
            context.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, a);
            context.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, a);
            context.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, a);
        }
    }

    /**
     * A pair of cat ears drawn above a panel's top edge. {@code flicker} (0..1) lets the
     * caller animate a subtle wiggle for the cute cosmetics toggle.
     */
    public static void catEars(DrawContext context, int x, int y, int width, int height, int color, float flicker) {
        int earWidth = Math.max(4, width / 6);
        int wiggle = Math.round(flicker * 2f);
        // Left ear: an outward triangle.
        for (int row = 0; row < height; row++) {
            int span = Math.round((float) earWidth * row / height);
            context.fill(x + earWidth - span, y + height - row - 1 + wiggle,
                    x + earWidth + 1, y + height - row + wiggle, color);
        }
        // Right ear, mirrored.
        for (int row = 0; row < height; row++) {
            int span = Math.round((float) earWidth * row / height);
            context.fill(x + width - earWidth - 1, y + height - row - 1 + wiggle,
                    x + width - earWidth + span, y + height - row + wiggle, color);
        }
    }

    private static void drawMask(DrawContext context, int x, int y, int size, int color, int[][] mask) {
        for (int row = 0; row < mask.length; row++) {
            for (int col = 0; col < mask[row].length; col++) {
                if (mask[row][col] == 1) {
                    context.fill(x + col * size, y + row * size, x + col * size + size, y + row * size + size, color);
                }
            }
        }
    }

    /** Corner inset for row {@code i} (0 = outermost) of a circle of radius {@code r}. */
    private static int cornerInset(int r, int i) {
        double dy = r - i - 0.5;
        double dx = Math.sqrt(Math.max(0, (double) r * r - dy * dy));
        return (int) Math.round(r - dx);
    }

    /** Horizontal inset for a given row of a rounded rectangle. */
    private static int rowInset(int row, int height, int r) {
        if (r <= 0) {
            return 0;
        }
        if (row < r) {
            return cornerInset(r, row);
        }
        if (row >= height - r) {
            return cornerInset(r, height - row - 1);
        }
        return 0;
    }

    private static int clampRadius(int radius, int width, int height) {
        int max = Math.min(width, height) / 2;
        return Math.max(0, Math.min(radius, max));
    }
}
