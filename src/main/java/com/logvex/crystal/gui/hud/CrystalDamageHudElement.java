package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Crystal damage indicators: the predicted damage from the current crystal placement to
 * the target and to the local player, colour-coded pink (target), cyan (self) and purple
 * (net trade). Numbers are kept large and high-contrast so they stay readable mid-fight.
 */
public final class CrystalDamageHudElement implements HudElement {

    @Override
    public String id() {
        return "crystal-damage";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null || client.player == null) {
            return;
        }
        if (bootstrap.combat().currentTarget() == null) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        double toTarget = bootstrap.combat().crystalDamageToTarget();
        double toSelf = bootstrap.combat().crystalDamageToSelf();
        double net = toTarget - toSelf;

        String[] lines = {
                "to target " + String.format("%.1f", toTarget),
                "to self   " + String.format("%.1f", toSelf),
                "net trade " + String.format("%+.1f", net)
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, false, true);
        HudText.line(context, client.textRenderer, lines[0], x, lineY, false, palette.crystalDamage());
        HudText.line(context, client.textRenderer, lines[1], x, lineY + HudText.LINE, false, palette.crystalSelf());
        HudText.line(context, client.textRenderer, lines[2], x, lineY + HudText.LINE * 2, false,
                net >= 0 ? palette.tertiary() : palette.warning());
    }

    @Override
    public int height(MinecraftClient client) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null || bootstrap.combat().currentTarget() == null) {
            return 0;
        }
        return 3 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 96;
    }
}
