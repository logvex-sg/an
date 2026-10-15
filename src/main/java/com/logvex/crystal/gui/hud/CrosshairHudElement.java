package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.Format;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * A compact readout of the block the crosshair is on: its name, coordinates and distance.
 *
 * <p>Reads only {@code MinecraftClient.crosshairTarget}, which the client already computes
 * every frame, so it adds no raycast of its own and costs nothing when the crosshair is not
 * on a block.
 */
public final class CrosshairHudElement implements com.logvex.crystal.gui.HudElement {

    @Override
    public String id() {
        return "crosshair";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null || client.player == null) {
            return;
        }
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = blockHit.getBlockPos();
        var state = client.world == null ? null : client.world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return;
        }
        String name = state.getBlock().getName().getString();
        double distance = client.player.getEntityPos().distanceTo(
                new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String[] lines = {
                Format.truncate(name, 22),
                Format.coords(pos.getX(), pos.getY(), pos.getZ()),
                Format.distance(distance)
        };
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, false, false);
        HudText.line(context, client.textRenderer, lines[0], x, lineY, false, palette.primary());
        HudText.line(context, client.textRenderer, lines[1], x, lineY + HudText.LINE, false, palette.textDim());
        HudText.line(context, client.textRenderer, lines[2], x, lineY + 2 * HudText.LINE, false, palette.secondary());
    }

    @Override
    public int height(MinecraftClient client) {
        return 3 * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 96;
    }
}
