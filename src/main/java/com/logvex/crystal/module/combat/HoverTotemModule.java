package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.TargetProtection;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Shows the predicted crystal damage if a crystal were placed at the player's own feet,
 * which is the standard way to check whether a self-crystal would kill. Read-only: it
 * never places anything.
 */
public final class HoverTotemModule extends Module {

    private final IntSetting range = add("Range", "Distance at which the check applies.", 4, 1, 8);
    private final BooleanSetting safeCheck = add("SafetyCheck", "Warn when self-crystal damage is lethal.", true);

    private double predictedSelfDamage;

    public HoverTotemModule() {
        super(Category.COMBAT, "HoverTotem", "Predicts self-crystal damage at your feet.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        var pos = client.player.getEntityPos();
        var result = context().combatService().predict(pos, pos, client.player.getHealth(),
                client.player.getArmor(), true,
                TargetProtection.resistanceAmplifier(client.player),
                TargetProtection.armorProtectionEpf(client.player));
        predictedSelfDamage = result.applied();
        if (safeCheck.value() && result.lethal()) {
            // Warn once per tick only when a crystal is actually within range.
            if (nearbyCrystal(client)) {
                context().notifications().warn("Self-crystal would be lethal (" +
                        String.format("%.1f", predictedSelfDamage) + ") \u2661");
            }
        }
    }

    private boolean nearbyCrystal(MinecraftClient client) {
        if (client.world == null) {
            return false;
        }
        for (var entity : client.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.decoration.EndCrystalEntity
                    && client.player.distanceTo(entity) <= range.value()) {
                return true;
            }
        }
        return false;
    }

    public double predictedSelfDamage() {
        return predictedSelfDamage;
    }
}
