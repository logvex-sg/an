package com.logvex.crystal.gui.hud;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.MiniAIManager;
import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A compact status readout for the mini-AI assistant: model state, queue depth, last latency
 * and the number of crashes captured this session. Hidden by default; the {@code AIAssistant}
 * module shows it.
 *
 * <p>It reads only cached counters from {@link MiniAIManager} and the crash monitor, so it never
 * loads the model, never runs inference and never touches the network while rendering.
 */
public final class AIHudElement implements HudElement {

    public static final String ID = "ai-assistant";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y,
                       boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        String[] lines = lines(bootstrap);
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length,
                rightAligned, true);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    private static String[] lines(CrystalClient bootstrap) {
        if (bootstrap == null || bootstrap.ai() == null) {
            return new String[]{"\u2661 ai", "starting"};
        }
        MiniAIManager ai = bootstrap.ai();
        AISettings settings = bootstrap.aiSettings();
        String header = "\u2661 ai " + ai.state().label();
        if (!settings.enabled) {
            return new String[]{header, "disabled"};
        }
        StringBuilder detail = new StringBuilder();
        int crashes = bootstrap.crashMonitor().size();
        detail.append(crashes).append(crashes == 1 ? " crash" : " crashes");
        if (ai.state() == MiniAIManager.State.READY) {
            if (ai.lastLatencyMillis() >= 0) {
                detail.append(" \u00B7 ").append(ai.lastLatencyMillis()).append("ms");
            }
            if (ai.queueDepth() > 0) {
                detail.append(" \u00B7 q").append(ai.queueDepth());
            }
        }
        return new String[]{header, detail.toString()};
    }

    @Override
    public int height(MinecraftClient client) {
        return 2 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return Math.max(96, HudText.width(client.textRenderer, lines(CrystalClient.get())));
    }
}
