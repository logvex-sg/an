package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Keystrokes: a compact WASD/mouse grid showing which movement keys are held.
 *
 * <p>Useful in anarchy PvP for recording and for seeing, at a glance, whether the client is
 * receiving the keys the player thinks they are pressing. Reads the live keybind state only.
 */
public final class KeystrokesHudElement implements HudElement {

    private static final int CELL = 15;
    private static final int GAP = 2;

    @Override
    public String id() {
        return "keystrokes";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        var options = client.options;
        // Row 1: [ ] [W] [ ]
        cell(context, palette, x + CELL + GAP, y, "W", options.forwardKey.isPressed());
        // Row 2: [A] [S] [D]
        int row2 = y + CELL + GAP;
        cell(context, palette, x, row2, "A", options.leftKey.isPressed());
        cell(context, palette, x + CELL + GAP, row2, "S", options.backKey.isPressed());
        cell(context, palette, x + (CELL + GAP) * 2, row2, "D", options.rightKey.isPressed());
        // Row 3: [LMB] [RMB]
        int row3 = row2 + CELL + GAP;
        cell(context, palette, x, row3, "LMB", options.attackKey.isPressed());
        cell(context, palette, x + CELL + GAP, row3, "RMB", options.useKey.isPressed());
    }

    private void cell(DrawContext context, Palette palette, int x, int y, String label, boolean pressed) {
        int fill = pressed ? palette.primary() : palette.panel();
        int border = pressed ? palette.accentGradientEnd() : palette.borderSoft();
        int text = pressed ? palette.textOnAccent() : palette.textDim();
        RenderUtil.roundedPanel(context, x, y, CELL, CELL, palette.radius(3), fill, border);
        int textX = x + (CELL - MinecraftClient.getInstance().textRenderer.getWidth(label)) / 2;
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, textX, y + 4, text);
    }

    @Override
    public int height(MinecraftClient client) {
        return CELL * 3 + GAP * 2 + 2;
    }

    @Override
    public int width(MinecraftClient client) {
        return CELL * 3 + GAP * 2;
    }
}
