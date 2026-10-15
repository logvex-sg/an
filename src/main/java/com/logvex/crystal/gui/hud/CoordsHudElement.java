package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Compact coordinates readout in the top-left column. */
public final class CoordsHudElement implements HudElement {

    @Override
    public String id() {
        return "coords";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null || client.player == null) {
            return;
        }
        var pos = client.player.getEntityPos();
        String[] lines = {
                String.format("x %.0f", pos.x),
                String.format("y %.0f", pos.y),
                String.format("z %.0f", pos.z)
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, false, false);
        for (int i = 0; i < lines.length; i++) {
            int color = switch (i) {
                case 0 -> palette.primary();
                case 1 -> palette.secondary();
                default -> palette.tertiary();
            };
            HudText.line(context, client.textRenderer, lines[i], x, lineY, false, color);
            lineY += HudText.LINE;
        }
    }

    @Override
    public int height(MinecraftClient client) {
        return 3 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 52;
    }
}
