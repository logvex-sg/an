package com.logvex.crystal.module.combat;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Highlights and assists crystal PvP by keeping the nearest crystal and target in view.
 *
 * <p>It is deliberately an <em>information</em> module: it tracks the closest end crystal
 * and target and reports them, but performs no automated placement, attacking or aiming.
 * Anarchy servers and the client's own scope treat unattended automation as out of bounds,
 * so the combat tools here assist the player's own input rather than replacing it.
 */
public final class AutoCrystalModule extends Module {

    private final IntSetting range = add("Range", "Search radius for crystals and targets.", 6, 2, 12);
    private final BooleanSetting notifyLethal = add("LethalAlert", "Warn when the target is in lethal range.", true);

    private Entity nearestCrystal;
    private Entity nearestTarget;
    private boolean lethalAlerted;

    public AutoCrystalModule() {
        super(Category.COMBAT, "AutoCrystal", "Tracks nearby crystals and targets and warns on lethal range.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        double radius = range.value();
        nearestCrystal = null;
        nearestTarget = null;
        double bestCrystal = Double.MAX_VALUE;
        double bestTarget = Double.MAX_VALUE;
        for (Entity entity : client.world.getEntities()) {
            double distance = client.player.distanceTo(entity);
            if (distance > radius) {
                continue;
            }
            if (entity instanceof EndCrystalEntity && distance < bestCrystal) {
                bestCrystal = distance;
                nearestCrystal = entity;
            } else if (entity instanceof PlayerEntity player && player != client.player && distance < bestTarget) {
                bestTarget = distance;
                nearestTarget = player;
            }
        }
        boolean lethal = context().combatService().targetLethal();
        if (lethal && !lethalAlerted && notifyLethal.value()) {
            context().notifications().warn("Lethal crystal range on target \u2661");
        }
        lethalAlerted = lethal;
    }

    @Override
    protected void onDisable() {
        nearestCrystal = null;
        nearestTarget = null;
        lethalAlerted = false;
    }

    public Entity nearestCrystal() {
        return nearestCrystal;
    }

    public Entity nearestTarget() {
        return nearestTarget;
    }
}
