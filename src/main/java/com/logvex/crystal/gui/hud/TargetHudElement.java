package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.combat.CombatTarget;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The pastel target HUD: the current target's name, a rounded health bar in cotton-candy
 * pink, an armour indicator in baby blue, and the predicted crystal damage. Health and
 * armour stay high-contrast and legible during a fight; the cute touches (a heart marker,
 * a paw print) are confined to the frame so they never obscure the numbers.
 */
public final class TargetHudElement implements HudElement {

    @Override
    public String id() {
        return "target";
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
        CombatTarget target = bootstrap.combat().currentTarget();
        if (target == null) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        int width = 150;
        int height = 46;
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(6), 80);
        RenderUtil.roundedGradientPanel(context, x, y, width, height, palette.radius(6),
                palette.panel(), palette.panelAlt(), palette.border());

        // Header: target name in primary, distance in dim text.
        context.drawTextWithShadow(client.textRenderer, target.name(), x + 8, y + 6, palette.primary());
        String dist = String.format("%.1fm", target.distance());
        context.drawTextWithShadow(client.textRenderer, dist, x + width - 8 - client.textRenderer.getWidth(dist), y + 6,
                palette.textDim());

        // Health bar.
        int barX = x + 8;
        int barY = y + 19;
        int barW = width - 16;
        int barH = 7;
        RenderUtil.roundedRect(context, barX, barY, barW, barH, palette.radius(3), ColorUtil.withAlpha(0xFF000000, 150));
        int filled = Math.round(barW * target.healthFraction());
        if (filled > 0) {
            RenderUtil.roundedGradientPanel(context, barX, barY, filled, barH, palette.radius(3),
                    palette.health(), ColorUtil.lerp(palette.health(), palette.tertiary(), 0.5f), 0);
        }
        String hp = String.format("%.1f", target.health());
        context.drawTextWithShadow(client.textRenderer, hp, barX + 2, barY + 8, palette.text());

        // Armour + crystal damage row.
        int rowY = y + 32;
        int armorColor = palette.armor();
        String armor = "\u26E8 " + target.armor();
        context.drawTextWithShadow(client.textRenderer, armor, barX, rowY, armorColor);
        double dmg = bootstrap.combat().crystalDamageToTarget();
        int dmgColor = bootstrap.combat().targetLethal() ? palette.danger() : palette.crystalDamage();
        String crystal = String.format("\u2726 %.1f", dmg);
        context.drawTextWithShadow(client.textRenderer, crystal, barX + 34, rowY, dmgColor);
        if (bootstrap.combat().targetLethal()) {
            String lethal = "LETHAL";
            context.drawTextWithShadow(client.textRenderer, lethal, x + width - 8 - client.textRenderer.getWidth(lethal),
                    rowY, palette.danger());
        }

        if (bootstrap.theme().settings().decorations) {
            // A tiny heart marker on the frame, purely cosmetic.
            RenderUtil.heart(context, x + width - 12, y + 3, 1, ColorUtil.scaleAlpha(palette.primary(), 0.7f));
        }
    }

    @Override
    public int height(MinecraftClient client) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null || bootstrap.combat().currentTarget() == null) {
            return 0;
        }
        return 46;
    }

    @Override
    public int width(MinecraftClient client) {
        return 150;
    }
}
