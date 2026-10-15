package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Draws a marker and label for every saved waypoint visible in the current dimension.
 *
 * <p>Waypoints are the anarchy navigation primitive: with no minimap this is how a player
 * finds a stash again. The marker is a thin vertical beam plus a small cube at the saved
 * block, and the label shows the name and the distance in blocks.
 */
public final class WaypointEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Only draw waypoints within this many blocks.", 512, 32, 4096);
    private final BooleanSetting showDistance = add("Distance", "Show distance in the label.", true);
    private final BooleanSetting beam = add("Beam", "Draw a vertical beam above each waypoint.", true);

    public WaypointEspModule() {
        super(Category.RENDER, "WaypointEsp", "Marks saved waypoints in the world.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || client.player == null || client.world == null) {
            return;
        }
        var player = client.player;
        Vec3d eye = player.getEntityPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        double maxSquared = (double) range.value() * range.value();

        for (var waypoint : bootstrap.waypoints().nearest(dimension, eye.x, eye.y, eye.z)) {
            double dx = waypoint.x() + 0.5 - eye.x;
            double dy = waypoint.y() + 0.5 - eye.y;
            double dz = waypoint.z() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > maxSquared) {
                continue;
            }
            int color = waypoint.color();
            if (beam.value()) {
                out.addBox(waypoint.x() + 0.35, waypoint.y(), waypoint.z() + 0.35,
                        waypoint.x() + 0.65, waypoint.y() + 4.0, waypoint.z() + 0.65, color, 1.5f);
            }
            out.addBox(waypoint.x(), waypoint.y(), waypoint.z(),
                    waypoint.x() + 1, waypoint.y() + 1, waypoint.z() + 1, color, 1.5f);
            String label = waypoint.name();
            if (showDistance.value()) {
                label += " " + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
            }
            out.addLabel(waypoint.x() + 0.5, waypoint.y() + 1.2, waypoint.z() + 0.5, label, color);
        }
    }
}
