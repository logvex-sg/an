package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.world.WorldInfoModule;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The world-information HUD panel. It owns no logic of its own: it asks the
 * {@link WorldInfoModule} for its configured lines and draws them in the standard themed
 * panel, so the on-screen readout and the {@code ; world} command are always identical.
 *
 * <p>The panel is hidden when its owning module is disabled or would show nothing, so a
 * user who turns every section off is not left with an empty box.
 */
public final class WorldInfoHudElement implements HudElement {

    @Override
    public String id() {
        return "worldinfo";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        WorldInfoModule module = bootstrap.modules().byClass(WorldInfoModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        String[] lines = module.buildLines(client);
        if (lines.length == 0) {
            return;
        }
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, rightAligned, true);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    @Override
    public int height(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return 0;
        }
        WorldInfoModule module = bootstrap.modules().byClass(WorldInfoModule.class);
        if (module == null || !module.isEnabled()) {
            return 0;
        }
        return module.buildLines(client).length * HudText.LINE + 6;
    }
}
