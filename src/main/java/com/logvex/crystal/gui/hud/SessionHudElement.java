package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.Format;
import com.logvex.crystal.world.SessionStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Session play time, distance walked and furthest travelled. */
public final class SessionHudElement implements HudElement {

    @Override
    public String id() {
        return "session";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float deltaTicks, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        SessionStats stats = SessionStats.get();
        String[] lines = {
                "session " + Format.duration(stats.activeMillis() / 1000L),
                "walked " + Format.distance(stats.distanceWalked()),
                "far " + Format.distance(stats.maxDistanceFromOrigin())
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, true, false);
        HudText.line(context, client.textRenderer, lines[0], x, lineY, true, palette.primary());
        HudText.line(context, client.textRenderer, lines[1], x, lineY + HudText.LINE, true, palette.textDim());
        HudText.line(context, client.textRenderer, lines[2], x, lineY + 2 * HudText.LINE, true, palette.textDim());
    }

    @Override
    public int height(MinecraftClient client) {
        return 3 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 84;
    }
}
