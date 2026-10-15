package com.logvex.crystal.module.combat;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Reports the local player's totem situation: how many are held, and which nearby threat
 * is closest. Informational only — it never swaps items or interacts with the inventory.
 */
public final class AutoTotemModule extends Module {

    private final IntSetting threatRange = add("ThreatRange", "Radius in which to look for threats.", 8, 2, 16);
    private final BooleanSetting warnEmpty = add("WarnEmpty", "Warn when no totem is held.", true);

    private int totemsHeld;
    private PlayerEntity nearestThreat;
    private boolean warned;

    public AutoTotemModule() {
        super(Category.COMBAT, "AutoTotem", "Reports totem count and the nearest threat.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        totemsHeld = countTotems(client);
        nearestThreat = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player != client.player) {
                double distance = client.player.distanceTo(player);
                if (distance <= threatRange.value() && distance < best) {
                    best = distance;
                    nearestThreat = player;
                }
            }
        }
        if (totemsHeld == 0 && nearestThreat != null && warnEmpty.value() && !warned) {
            context().notifications().error("No totem held and a player is nearby \u2661");
            warned = true;
        } else if (totemsHeld > 0) {
            warned = false;
        }
    }

    private int countTotems(MinecraftClient client) {
        int count = 0;
        var inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int totemsHeld() {
        return totemsHeld;
    }

    public PlayerEntity nearestThreat() {
        return nearestThreat;
    }
}
