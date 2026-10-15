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
 * AutoMend: uses experience bottles to mend damaged armour through the Mending enchantment
 * when the player is safe.
 *
 * <p>Only uses bottles already carried and only when no combat target is nearby, so it does
 * not waste them mid-fight. The use is a normal item interaction.
 */
public final class AutoMendModule extends Module {

    private final IntSetting durabilityThreshold = add("DurabilityThreshold",
            "Mend when the worst armour piece is below this durability percentage.", 60, 1, 100);
    private final BooleanSetting onlyWhenSafe = add("OnlyWhenSafe",
            "Only mend when no combat target is nearby.", true);
    private final IntSetting interval = add("Interval", "Minimum ticks between bottles.", 20, 5, 100);

    private int ticks;

    public AutoMendModule() {
        super(Category.PLAYER, "AutoMend", "Mends damaged armour with experience bottles when safe.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.currentScreen != null) {
            return;
        }
        if (onlyWhenSafe.value() && context().combatService().currentTarget() != null) {
            return;
        }
        if (worstArmorPercent(client) >= durabilityThreshold.value()) {
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        int slot = InventoryUtil.findInHotbar(client, Items.EXPERIENCE_BOTTLE);
        if (slot < 0) {
            return;
        }
        InventoryUtil.selectSlot(client, slot);
        if (InventoryUtil.heldStack(client).isOf(Items.EXPERIENCE_BOTTLE)) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        }
    }

    private int worstArmorPercent(MinecraftClient client) {
        int worst = 100;
        for (var slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack stack = client.player.getEquippedStack(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) {
                continue;
            }
            int remaining = stack.getMaxDamage() - stack.getDamage();
            worst = Math.min(worst, (int) (remaining * 100L / stack.getMaxDamage()));
        }
        return worst;
    }
}
