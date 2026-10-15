package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.entity.EntityCategory;
import com.logvex.crystal.entity.EntityQuery;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A per-category entity counter sourced from the shared {@link EntityQuery} snapshot, so
 * the HUD and the entity ESP modules all read the same numbers from one scan rather than
 * each walking the world.
 */
public final class EntityCountHudElement implements HudElement {

    @Override
    public String id() {
        return "entitycount";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        var counts = EntityQuery.get().counts();
        String[] lines = {
                "entities " + EntityQuery.get().size(),
                "players " + counts.getOrDefault(EntityCategory.PLAYER, 0),
                "mobs " + (counts.getOrDefault(EntityCategory.HOSTILE, 0)
                        + counts.getOrDefault(EntityCategory.PASSIVE, 0)),
                "items " + counts.getOrDefault(EntityCategory.ITEM, 0)
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, true, false);
        int[] colors = {palette.primary(), palette.danger(), palette.tertiary(), palette.warning()};
        for (int i = 0; i < lines.length; i++) {
            HudText.line(context, client.textRenderer, lines[i], x, lineY, true, colors[i]);
            lineY += HudText.LINE;
        }
    }

    @Override
    public int height(MinecraftClient client) {
        return 4 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 76;
    }
}
