package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared HUD drawing helpers: a consistent rounded panel background with an optional
 * accent header bar, plus left/right aligned lines. Every HUD element uses these so
 * spacing, rounding and colours match the rest of the theme.
 *
 * <p>Each colour is faded by the active element's opacity (from the HUD layout), so the
 * per-element opacity setting reaches every panel without each element handling it.
 */
final class HudText {

    static final int LINE = 10;

    private HudText() {
    }

    /** The opacity of the element currently being drawn (1.0 outside an element). */
    static float opacity() {
        CrystalClient bootstrap = CrystalClient.get();
        return bootstrap == null ? 1f : bootstrap.hud().currentOpacity();
    }

    /** Fades a colour by the active element's opacity. */
    static int tint(int color) {
        return ColorUtil.scaleAlpha(color, opacity());
    }

    /**
     * Draws a translucent rounded panel behind {@code lines} rows and returns the top y
     * for the first text line. When {@code accentHeader} is true a thin gradient bar is
     * drawn along the top edge as a decorative category marker.
     */
    static int panel(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                     int width, int lines, boolean rightAligned, boolean accentHeader) {
        int height = lines * LINE + 6;
        int left = rightAligned ? x - width : x;
        int fill = tint(palette.panel());
        RenderUtil.softShadow(context, left, y, width, height, palette.radius(4), 60);
        RenderUtil.roundedPanel(context, left, y, width, height, palette.radius(4), fill, tint(palette.borderSoft()));
        if (accentHeader) {
            RenderUtil.horizontalGradient(context, left + 2, y + 1, width - 4, 2,
                    tint(palette.accentGradientStart()), tint(palette.accentGradientEnd()));
        }
        return y + 4;
    }

    static void line(DrawContext context, TextRenderer renderer, String text, int x, int y,
                     boolean rightAligned, int color) {
        int drawX = rightAligned ? x - renderer.getWidth(text) - 3 : x + 3;
        context.drawTextWithShadow(renderer, text, drawX, y, tint(color));
    }

    static int width(TextRenderer renderer, String... lines) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, renderer.getWidth(line));
        }
        return max + 8;
    }
}
