package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * AutoRefill: tops up the hotbar with the anarchy staples — obsidian, end crystals, golden
 * apples, ender pearls and totems — from the backpack, using the vanilla quick-move action.
 *
 * <p>Each item is kept at or above a configurable count. Only items the player already carries
 * are moved, and only into empty hotbar slots, so it never displaces a slot the player set up.
 */
public final class AutoRefillModule extends Module {

    private final BooleanSetting obsidian = add("Obsidian", "Keep obsidian stocked.", true);
    private final BooleanSetting crystals = add("EndCrystal", "Keep end crystals stocked.", true);
    private final BooleanSetting gapples = add("GoldenApple", "Keep golden apples stocked.", true);
    private final BooleanSetting pearls = add("EnderPearl", "Keep ender pearls stocked.", true);
    private final BooleanSetting totems = add("Totem", "Keep totems stocked.", false);
    private final IntSetting minimum = add("Minimum", "Refill a stack below this count.", 8, 1, 64);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between moves.", 4, 1, 40);

    private int ticks;

    public AutoRefillModule() {
        super(Category.PLAYER, "AutoRefill", "Tops up the hotbar with obsidian, crystals, gapples and pearls.");
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
        for (Item item : wantedItems()) {
            if (hotbarCount(client, item) >= minimum.value()) {
                continue;
            }
            int backpack = firstBackpackSlot(client, item);
            if (backpack < 0) {
                continue;
            }
            int empty = emptyHotbarSlot(client);
            if (empty < 0) {
                return;
            }
            InventoryUtil.moveToHotbar(client, backpack, empty);
            ticks = 0;
            return;
        }
    }

    private List<Item> wantedItems() {
        List<Item> items = new ArrayList<>();
        if (obsidian.value()) {
            items.add(Items.OBSIDIAN);
        }
        if (crystals.value()) {
            items.add(Items.END_CRYSTAL);
        }
        if (gapples.value()) {
            items.add(Items.GOLDEN_APPLE);
        }
        if (pearls.value()) {
            items.add(Items.ENDER_PEARL);
        }
        if (totems.value()) {
            items.add(Items.TOTEM_OF_UNDYING);
        }
        return items;
    }

    private static int hotbarCount(MinecraftClient client, Item item) {
        int total = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = InventoryUtil.stackAt(client, slot);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int firstBackpackSlot(MinecraftClient client, Item item) {
        for (int slot = InventoryUtil.BACKPACK_START; slot < InventoryUtil.MAIN_SIZE; slot++) {
            if (InventoryUtil.stackAt(client, slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static int emptyHotbarSlot(MinecraftClient client) {
        for (int slot = 0; slot < 9; slot++) {
            if (InventoryUtil.stackAt(client, slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }
}
