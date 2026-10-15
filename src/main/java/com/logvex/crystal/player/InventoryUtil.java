package com.logvex.crystal.player;

import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Small, shared helpers for reading and reordering the local player's inventory.
 *
 * <p>Every method here is a thin wrapper over the vanilla inventory and interaction manager:
 * nothing reads hidden server state, and any change (slot selection, an inventory click) is
 * performed through the normal client path so the server still validates it. Keeping this in
 * one place is what lets the player and combat modules stay a handful of lines each.
 *
 * <p>Hotbar indices are 0..8. Main-inventory indices follow the vanilla convention where 0..8
 * are the hotbar and 9..35 are the rest, matching {@link PlayerInventory#getStack(int)}.
 */
public final class InventoryUtil {

    /** The player's main inventory size (hotbar + backpack). */
    public static final int MAIN_SIZE = 36;
    /** First backpack (non-hotbar) slot. */
    public static final int BACKPACK_START = 9;
    /** The offhand screen slot in the player screen handler. */
    private static final int OFFHAND_SCREEN_SLOT = 45;

    private InventoryUtil() {
    }

    /** The item in the player's currently selected hotbar slot, or an empty stack. */
    public static ItemStack heldStack(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        return player == null ? ItemStack.EMPTY : player.getInventory().getSelectedStack();
    }

    /** The stack in the given main-inventory slot (0..35), or an empty stack. */
    public static ItemStack stackAt(MinecraftClient client, int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null || slot < 0 || slot >= MAIN_SIZE) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getStack(slot);
    }

    /** The first hotbar slot (0..8) holding {@code item}, or -1. */
    public static int findInHotbar(MinecraftClient client, Item item) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** The first main-inventory slot (0..35) holding {@code item}, or -1. */
    public static int findInInventory(MinecraftClient client, Item item) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** The first slot (0..35) matching the predicate, or -1. */
    public static int findSlot(MinecraftClient client, Predicate<ItemStack> predicate) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** Total count of {@code item} across the main inventory. */
    public static int count(MinecraftClient client, Item item) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Whether the player's offhand holds {@code item}. */
    public static boolean offhandIs(MinecraftClient client, Item item) {
        ClientPlayerEntity player = client.player;
        return player != null && player.getOffHandStack().isOf(item);
    }

    /**
     * Selects a hotbar slot and tells the server, the same way the vanilla client does when
     * the player scrolls the hotbar. No-op if the slot is already selected.
     */
    public static void selectSlot(MinecraftClient client, int hotbarSlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || hotbarSlot < 0 || hotbarSlot >= PlayerInventory.getHotbarSize()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == hotbarSlot) {
            return;
        }
        inventory.setSelectedSlot(hotbarSlot);
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }
    }

    /**
     * Swaps a backpack slot (9..35) into a hotbar slot using the vanilla SWAP inventory action
     * — the same packet the client sends for a shift-click swap. In the player screen handler
     * a backpack slot's inventory index equals its screen slot, so the source needs no
     * translation. Safe with no container open: the player's own screen handler is used.
     */
    public static boolean moveToHotbar(MinecraftClient client, int backpackSlot, int hotbarSlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return false;
        }
        if (backpackSlot < BACKPACK_START || backpackSlot >= MAIN_SIZE) {
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot >= PlayerInventory.getHotbarSize()) {
            return false;
        }
        int syncId = player.playerScreenHandler.syncId;
        client.interactionManager.clickSlot(syncId, backpackSlot, hotbarSlot, SlotActionType.SWAP, player);
        return true;
    }

    /**
     * Swaps the offhand item with a hotbar slot via the vanilla SWAP action. In the SWAP
     * action the button is the inventory index to swap against, so {@code 0..8} selects a
     * hotbar slot and {@code 40} the offhand; here the offhand screen slot is clicked with the
     * hotbar slot as the button.
     */
    public static boolean swapOffhand(MinecraftClient client, int hotbarSlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot >= PlayerInventory.getHotbarSize()) {
            return false;
        }
        int syncId = player.playerScreenHandler.syncId;
        client.interactionManager.clickSlot(syncId, OFFHAND_SCREEN_SLOT, hotbarSlot,
                SlotActionType.SWAP, player);
        return true;
    }

    /** True if an item is a golden or enchanted golden apple. */
    public static boolean isGapple(Item item) {
        return item == net.minecraft.item.Items.GOLDEN_APPLE
                || item == net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE;
    }

    /** Remaining durability as a 0..1 fraction; 1 for a non-damageable or empty stack. */
    public static float durabilityFraction(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return 1f;
        }
        return (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
    }
}
