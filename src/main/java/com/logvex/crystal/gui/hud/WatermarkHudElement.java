package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The client watermark: the LOGVEX CRYSTAL name with a decorative cat-ear motif and a
 * sparkle, drawn in the top-left corner. Uses the active palette's accent gradient.
 */
public final class WatermarkHudElement implements HudElement {

    @Override
    public String id() {
        return "watermark";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        String name = "LOGVEX";
        String suffix = " CRYSTAL";
        int width = client.textRenderer.getWidth(name + suffix) + 14;
        int height = 16;
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(4), 70);
        RenderUtil.roundedGradientPanel(context, x, y, width, height, palette.radius(4),
                palette.panel(), palette.panelAlt(), palette.border());
        if (bootstrap.theme().settings().decorations) {
            // A pair of little cat ears on top of the badge, wiggling gently.
            float wiggle = (float) Math.sin(System.currentTimeMillis() / 900.0) * 0.5f + 0.5f;
            RenderUtil.catEars(context, x + 3, y - 5, 14, 5, palette.primary(), wiggle);
        }
        int textX = x + 7;
        context.drawTextWithShadow(client.textRenderer, name, textX, y + 4, palette.primary());
        textX += client.textRenderer.getWidth(name);
        context.drawTextWithShadow(client.textRenderer, suffix, textX, y + 4, palette.secondary());
        if (bootstrap.theme().settings().decorations) {
            RenderUtil.star(context, x + width - 6, y + 8, 3, palette.tertiary());
        }
    }

    @Override
    public int height(MinecraftClient client) {
        return 16;
    }

    @Override
    public int width(MinecraftClient client) {
        return client.textRenderer.getWidth("LOGVEX CRYSTAL") + 14;
    }
}
