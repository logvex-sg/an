package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * AutoArmor: equips the best armour piece carried for any slot that is empty or worn out,
 * using the vanilla quick-move ("shift-click") inventory action on the player's own screen
 * handler — the same click the client sends when you shift-click armour in your inventory.
 *
 * <p>It only ever moves armour the player already has, and only when the worn piece is worse
 * by a simple defence-point comparison, so it will not churn good armour.
 */
public final class AutoArmorModule extends Module {

    private final IntSetting durabilityThreshold = add("DurabilityThreshold",
            "Replace a worn piece below this durability percentage.", 20, 0, 100);
    private final BooleanSetting swapWorse = add("SwapWorse",
            "Also replace a piece with a strictly better one carried.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between equips.", 5, 1, 40);

    private int ticks;

    public AutoArmorModule() {
        super(Category.PLAYER, "AutoArmor", "Equips the best armour carried into empty or worn slots.");
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
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = client.player.getEquippedStack(slot);
            if (!needsReplacing(worn)) {
                continue;
            }
            int best = bestArmorSlot(client, slot);
            if (best < 0) {
                continue;
            }
            int screenSlot = armorScreenSlot(best);
            if (screenSlot < 0) {
                continue;
            }
            client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId,
                    screenSlot, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
            ticks = 0;
            return;
        }
    }

    private boolean needsReplacing(ItemStack worn) {
        if (worn.isEmpty()) {
            return true;
        }
        return InventoryUtil.durabilityFraction(worn) * 100f < durabilityThreshold.value();
    }

    /** The main-inventory slot of the best armour piece for {@code slot}, or -1. */
    private int bestArmorSlot(MinecraftClient client, EquipmentSlot slot) {
        ItemStack worn = client.player.getEquippedStack(slot);
        boolean replacing = needsReplacing(worn);
        int wornDefence = defence(worn);
        int best = -1;
        int bestDefence = replacing ? -1 : wornDefence;
        for (int i = 0; i < InventoryUtil.MAIN_SIZE; i++) {
            ItemStack stack = InventoryUtil.stackAt(client, i);
            if (!isArmorFor(stack, slot)) {
                continue;
            }
            int defence = defence(stack);
            if (defence <= bestDefence) {
                continue;
            }
            if (!replacing && !swapWorse.value()) {
                continue;
            }
            bestDefence = defence;
            best = i;
        }
        return best;
    }

    private static boolean isArmorFor(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return false;
        }
        var item = stack.getItem();
        return switch (slot) {
            case HEAD -> item == Items.NETHERITE_HELMET || item == Items.DIAMOND_HELMET
                    || item == Items.IRON_HELMET || item == Items.GOLDEN_HELMET
                    || item == Items.CHAINMAIL_HELMET || item == Items.LEATHER_HELMET;
            case CHEST -> item == Items.NETHERITE_CHESTPLATE || item == Items.DIAMOND_CHESTPLATE
                    || item == Items.IRON_CHESTPLATE || item == Items.GOLDEN_CHESTPLATE
                    || item == Items.CHAINMAIL_CHESTPLATE || item == Items.LEATHER_CHESTPLATE;
            case LEGS -> item == Items.NETHERITE_LEGGINGS || item == Items.DIAMOND_LEGGINGS
                    || item == Items.IRON_LEGGINGS || item == Items.GOLDEN_LEGGINGS
                    || item == Items.CHAINMAIL_LEGGINGS || item == Items.LEATHER_LEGGINGS;
            case FEET -> item == Items.NETHERITE_BOOTS || item == Items.DIAMOND_BOOTS
                    || item == Items.IRON_BOOTS || item == Items.GOLDEN_BOOTS
                    || item == Items.CHAINMAIL_BOOTS || item == Items.LEATHER_BOOTS;
            default -> false;
        };
    }

    private static int defence(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        var item = stack.getItem();
        if (item == Items.NETHERITE_HELMET || item == Items.NETHERITE_BOOTS) {
            return 3;
        }
        if (item == Items.NETHERITE_CHESTPLATE) {
            return 8;
        }
        if (item == Items.NETHERITE_LEGGINGS) {
            return 6;
        }
        if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_BOOTS) {
            return 3;
        }
        if (item == Items.DIAMOND_CHESTPLATE) {
            return 8;
        }
        if (item == Items.DIAMOND_LEGGINGS) {
            return 6;
        }
        return 1;
    }

    /** Maps a main-inventory slot to its screen slot in the player screen handler. */
    private static int armorScreenSlot(int mainSlot) {
        return mainSlot < InventoryUtil.BACKPACK_START ? mainSlot + 36 : mainSlot;
    }
}
