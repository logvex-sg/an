package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A HUD element that renders a small list of text lines in the standard themed panel.
 *
 * <p>Most readouts (coordinates, biome, time, ping, ...) are just "a few strings in a
 * rounded box", so rather than repeat that drawing in every element this class captures it
 * once. A concrete element only supplies its {@link HudLines}, its column and a preferred
 * width; the panel, spacing, opacity and alignment are shared.
 */
public final class SimpleHudElement implements HudElement {

    private final String id;
    private final Column column;
    private final int preferredWidth;
    private final boolean accentHeader;
    private final HudLines lines;

    public SimpleHudElement(String id, Column column, int preferredWidth, boolean accentHeader, HudLines lines) {
        this.id = id;
        this.column = column;
        this.preferredWidth = preferredWidth;
        this.accentHeader = accentHeader;
        this.lines = lines;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Column column() {
        return column;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        String[] lines = this.lines.lines(client);
        if (lines == null || lines.length == 0) {
            return;
        }
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, rightAligned, accentHeader);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    @Override
    public int height(MinecraftClient client) {
        String[] current = lines.lines(client);
        int count = current == null ? 0 : current.length;
        return count * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        String[] current = lines.lines(client);
        if (current == null || current.length == 0) {
            return preferredWidth;
        }
        return Math.max(preferredWidth, HudText.width(client.textRenderer, current));
    }
}
