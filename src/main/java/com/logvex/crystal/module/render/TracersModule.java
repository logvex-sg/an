package com.logvex.crystal.module.render;

import com.logvex.crystal.gui.OverlayRenderer;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Tracers: a directional indicator at the crosshair pointing toward nearby players and
 * end crystals.
 *
 * <p>Implemented as a 2-D compass overlay rather than true 3-D lines. A 2-D overlay needs
 * no matrix maths, cannot corrupt world render state and costs a fixed few pixels per
 * entity, so it stays cheap in a fight. The angle is relative to where the player is
 * looking, so the marker sits on the correct side of the screen.
 */
public final class TracersModule extends Module implements OverlayRenderer {

    private final IntSetting range = add("Range", "Maximum distance to draw a tracer for.", 32, 8, 96);
    private final BooleanSetting players = add("Players", "Draw tracers toward players.", true);
    private final BooleanSetting crystals = add("Crystals", "Draw tracers toward end crystals.", true);
    private final BooleanSetting distance = add("Distance", "Show the distance beside each marker.", true);

    private boolean registered;

    public TracersModule() {
        super(Category.RENDER, "Tracers", "Directional indicators toward players and crystals.");
    }

    @Override
    protected void onEnable() {
        context().hudManager().registerOverlay(this);
        registered = true;
    }

    @Override
    protected void onDisable() {
        if (registered) {
            context().hudManager().removeOverlay(this);
            registered = false;
        }
    }

    @Override
    public void renderOverlay(DrawContext context, MinecraftClient client, float tickDelta) {
        if (client.player == null || client.world == null) {
            return;
        }
        var palette = context().theme().palette();
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        double maxDistance = range.value();
        float yaw = (float) Math.toRadians(client.player.getYaw());

        for (Entity entity : client.world.getEntities()) {
            boolean isPlayer = players.value() && entity instanceof PlayerEntity && entity != client.player;
            boolean isCrystal = crystals.value() && entity instanceof EndCrystalEntity;
            if (!isPlayer && !isCrystal) {
                continue;
            }
            double dist = client.player.distanceTo(entity);
            if (dist > maxDistance) {
                continue;
            }
            Vec3d delta = entity.getEntityPos().subtract(client.player.getEntityPos());
            // Angle of the entity relative to the direction the player faces.
            double entityAngle = Math.atan2(delta.z, delta.x);
            double relative = entityAngle - (yaw - Math.PI / 2);
            float dx = (float) Math.cos(relative);
            float dy = (float) Math.sin(relative);
            int length = 26;
            int x2 = centerX + Math.round(dx * length);
            int y2 = centerY + Math.round(dy * length);
            int color = isPlayer ? palette.danger() : palette.crystalDamage();
            // A short line from the crosshair out to the marker.
            int steps = 20;
            for (int i = 1; i <= steps; i++) {
                int px = centerX + Math.round(dx * length * i / steps);
                int py = centerY + Math.round(dy * length * i / steps);
                context.fill(px, py, px + 1, py + 1, ColorUtil.scaleAlpha(color, 0.5f));
            }
            RenderUtil.dot(context, x2, y2, 2, color);
            if (distance.value()) {
                context.drawTextWithShadow(client.textRenderer, String.format("%.0f", dist), x2 + 4, y2 - 3,
                        palette.textDim());
            }
        }
    }
}
