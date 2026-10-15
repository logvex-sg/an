package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** FPS, tick cost and the local player's ping, in the top-right column. */
public final class PerformanceHudElement implements HudElement {

    @Override
    public String id() {
        return "performance";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        int fps = client.getCurrentFps();
        int ping = ping(client);
        String[] lines = {
                "fps " + fps,
                "ping " + (ping < 0 ? "-" : ping + "ms")
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, true, false);
        int fpsColor = fps >= 120 ? palette.secondary() : (fps >= 60 ? palette.primary() : palette.warning());
        int pingColor = ping < 0 ? palette.textDim() : (ping <= 80 ? palette.secondary() : palette.warning());
        HudText.line(context, client.textRenderer, lines[0], x, lineY, true, fpsColor);
        HudText.line(context, client.textRenderer, lines[1], x, lineY + HudText.LINE, true, pingColor);
        if (bootstrap.theme().settings().decorations) {
            RenderUtil.paw(context, x - width - 8, y + 3, 1, palette.tertiary());
        }
    }

    private int ping(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            return -1;
        }
        var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    @Override
    public int height(MinecraftClient client) {
        return 2 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 64;
    }
}
