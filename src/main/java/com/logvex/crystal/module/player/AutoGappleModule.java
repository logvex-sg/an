package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * AutoGapple: eats a golden apple when health drops below a threshold, using the vanilla
 * item-use action. Enchanted gapples are preferred when carried.
 *
 * <p>It only eats apples already in the hotbar; it never moves items to make one available.
 * A cooldown keeps it from eating a whole stack the instant health dips.
 */
public final class AutoGappleModule extends Module {

    private final IntSetting threshold = add("Threshold", "Eat when health is at or below this.", 14, 1, 20);
    private final BooleanSetting preferEnchanted = add("PreferEnchanted",
            "Use an enchanted golden apple when one is carried.", true);
    private final BooleanSetting onlyInCombat = add("OnlyInCombat",
            "Only eat while a target is nearby.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between apples.", 20, 5, 100);

    private int ticks;

    public AutoGappleModule() {
        super(Category.PLAYER, "AutoGapple", "Eats a golden apple when health is low.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.currentScreen != null) {
            return;
        }
        if (client.player.getHealth() > threshold.value()) {
            return;
        }
        if (onlyInCombat.value() && context().combatService().currentTarget() == null) {
            return;
        }
        if (++ticks < cooldown.value()) {
            return;
        }
        ticks = 0;
        int slot = chooseSlot(client);
        if (slot < 0) {
            return;
        }
        InventoryUtil.selectSlot(client, slot);
        ItemStack held = InventoryUtil.heldStack(client);
        if (InventoryUtil.isGapple(held.getItem())) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        }
    }

    private int chooseSlot(MinecraftClient client) {
        if (preferEnchanted.value()) {
            int enchanted = InventoryUtil.findInHotbar(client, Items.ENCHANTED_GOLDEN_APPLE);
            if (enchanted >= 0) {
                return enchanted;
            }
        }
        return InventoryUtil.findInHotbar(client, Items.GOLDEN_APPLE);
    }
}
