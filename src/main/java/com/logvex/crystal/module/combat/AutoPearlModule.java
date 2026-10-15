package com.logvex.crystal.module.combat;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * AutoPearl: throws an ender pearl to escape when the player is about to take lethal crystal
 * damage or is critically low, using the vanilla item-use action.
 *
 * <p>The decision reuses the same crystal-damage prediction the HUD shows, so it only fires
 * when the incoming blast would actually kill. A cooldown prevents repeat throws.
 */
public final class AutoPearlModule extends Module {

    private final BooleanSetting onLethal = add("OnLethal",
            "Throw when the predicted crystal damage would kill.", true);
    private final IntSetting healthThreshold = add("HealthThreshold",
            "Also throw when health drops to this level.", 6, 1, 20);
    private final BooleanSetting throwWhenFalling = add("ThrowWhenFalling",
            "Throw when falling more than a few blocks.", false);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between throws.", 20, 5, 100);

    private int ticks;

    public AutoPearlModule() {
        super(Category.COMBAT, "AutoPearl", "Throws an ender pearl to escape lethal damage.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.currentScreen != null) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        if (!shouldThrow(client)) {
            return;
        }
        int slot = InventoryUtil.findInHotbar(client, Items.ENDER_PEARL);
        if (slot < 0) {
            return;
        }
        InventoryUtil.selectSlot(client, slot);
        if (InventoryUtil.heldStack(client).isOf(Items.ENDER_PEARL)) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            ticks = 0;
        }
    }

    private boolean shouldThrow(MinecraftClient client) {
        if (onLethal.value()
                && context().combatService().crystalDamageToSelf() >= client.player.getHealth()) {
            return true;
        }
        if (client.player.getHealth() <= healthThreshold.value()) {
            return true;
        }
        return throwWhenFalling.value() && client.player.fallDistance > 4.0f;
    }
}
