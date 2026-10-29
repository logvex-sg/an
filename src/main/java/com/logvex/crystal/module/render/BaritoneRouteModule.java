package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Draws a world-space marker at Baritone's current goal.
 *
 * <p>Baritone shows a faint target box of its own, but anarchy players want a clearer, themed
 * beacon at the destination. This module reads the goal position from the shared
 * {@link BaritoneService} snapshot (so it works for goals set from anywhere) and draws a thin
 * vertical beam plus a labelled cube. It contributes nothing when no goal is set or Baritone is
 * unavailable, and it never touches Baritone directly.
 */
public final class BaritoneRouteModule extends WorldEspModule {

    private final BooleanSetting beam = add("Beam", "Draw a vertical beam above the goal.", true);
    private final BooleanSetting label = add("Label", "Label the goal with its distance.", true);

    public BaritoneRouteModule() {
        super(Category.RENDER, "BaritoneRoute", "Marks the active Baritone goal in the world.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || client.player == null) {
            return;
        }
        BaritoneService service = bootstrap.baritone();
        BaritoneStatus status = service.status();
        if (!status.hasGoalPosition()) {
            return;
        }
        int color = bootstrap.theme().palette().accentGradientStart();
        double x = status.goalX();
        double z = status.goalZ();
        double y = status.goalY() == BaritoneStatus.NO_COORD ? client.player.getY() : status.goalY();
        if (beam.value()) {
            out.addBox(x + 0.35, y, z + 0.35, x + 0.65, y + 6.0, z + 0.65, color, 2.0f);
        }
        out.addBox(x, y, z, x + 1, y + 1, z + 1, color, 2.0f);
        if (label.value()) {
            double dx = x + 0.5 - client.player.getX();
            double dz = z + 0.5 - client.player.getZ();
            out.addLabel(x + 0.5, y + 1.3, z + 0.5,
                    "goal " + (int) Math.sqrt(dx * dx + dz * dz) + "m", color);
        }
    }
}
