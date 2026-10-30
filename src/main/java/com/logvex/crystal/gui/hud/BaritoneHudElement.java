package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The HUD readout for the Baritone integration.
 *
 * <p>Registered by the HUD manager but hidden by default; the {@code Baritone} module shows it.
 * It reads the shared {@link BaritoneService} snapshot (never Baritone directly) and draws a
 * compact two-line panel: a status line and, when a goal position is known, the distance to it.
 * When Baritone is unavailable it shows the reason instead, so the player is told why.
 */
public final class BaritoneHudElement implements HudElement {

    public static final String ID = "baritone";

    @Override
    public String id() {
        return ID;
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
        String[] lines = lines(client);
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length,
                rightAligned, true);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    private static String[] lines(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        BaritoneService service = bootstrap == null ? null : bootstrap.baritone();
        BaritoneStatus status = service == null ? BaritoneStatus.unavailable("client not ready") : service.status();
        if (!status.available()) {
            return new String[]{"baritone", status.reason().isEmpty() ? "unavailable" : status.reason()};
        }
        String header = "baritone " + status.version();
        String detail = status.statusText();
        if (status.hasGoalPosition() && client.player != null) {
            double dx = status.goalX() + 0.5 - client.player.getX();
            double dz = status.goalZ() + 0.5 - client.player.getZ();
            detail += " \u00B7 " + (int) Math.sqrt(dx * dx + dz * dz) + "m";
        }
        return new String[]{header, detail};
    }

    @Override
    public int height(MinecraftClient client) {
        return 2 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return Math.max(90, HudText.width(client.textRenderer, lines(client)));
    }
}
