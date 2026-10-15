package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.theme.Palette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Lists every enabled module, sorted by name, in the top-right column. */
public final class ModuleListHudElement implements HudElement {

    private final ModuleManager modules;
    private List<String> cached;
    private int cachedEnabledCount = -1;

    public ModuleListHudElement(ModuleManager modules) {
        this.modules = modules;
    }

    @Override
    public String id() {
        return "module-list";
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
        List<String> names = enabledNames();
        if (names.isEmpty()) {
            return;
        }
        int width = HudText.width(client.textRenderer, names.toArray(new String[0]));
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, names.size(), true, true);
        for (String name : names) {
            // Alternate pink/lavender so the list reads as part of the pastel identity.
            int color = (names.indexOf(name) % 2 == 0) ? palette.primary() : palette.secondary();
            HudText.line(context, client.textRenderer, name, x, lineY, true, color);
            lineY += HudText.LINE;
        }
    }

    private List<String> enabledNames() {
        int count = modules.enabledCount();
        if (cached == null || count != cachedEnabledCount) {
            List<String> names = new ArrayList<>();
            for (Module module : modules.all()) {
                if (module.isEnabled()) {
                    names.add(module.name());
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            cached = names;
            cachedEnabledCount = count;
        }
        return cached;
    }

    @Override
    public int height(MinecraftClient client) {
        return Math.max(0, enabledNames().size()) * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        List<String> names = enabledNames();
        if (names.isEmpty()) {
            return 60;
        }
        return HudText.width(client.textRenderer, names.toArray(new String[0]));
    }
}
