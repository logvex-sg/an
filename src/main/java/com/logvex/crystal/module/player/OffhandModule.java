package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Offhand: keeps a chosen item in the offhand slot by swapping it in from the hotbar when the
 * slot is empty (or holds the wrong item).
 *
 * <p>Uses the vanilla offhand-swap action, so the server sees an ordinary inventory click. A
 * short cooldown keeps it from fighting the player if they swap manually.
 */
public final class OffhandModule extends Module {

    /** Offhand target items, in preference order. */
    private enum Target {
        TOTEM, CRYSTAL, GAPPLE, SHIELD
    }

    private final EnumSetting<Target> target =
            add("Item", "Which item to keep in the offhand.", Target.TOTEM);
    private final BooleanSetting refill = add("Refill", "Move a fresh stack in when it runs out.", true);
    private final IntSetting cooldown = add("Cooldown", "Minimum ticks between swaps.", 5, 1, 40);

    private int ticks;

    public OffhandModule() {
        super(Category.PLAYER, "Offhand", "Keeps a chosen item in the offhand slot.");
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
        var item = itemFor(target.value());
        if (item == null) {
            return;
        }
        if (InventoryUtil.offhandIs(client, item)) {
            return;
        }
        int slot = InventoryUtil.findInHotbar(client, item);
        if (slot < 0) {
            if (!refill.value()) {
                return;
            }
            int backpack = InventoryUtil.findInInventory(client, item);
            if (backpack < InventoryUtil.BACKPACK_START) {
                return;
            }
            int emptyHotbar = emptyHotbarSlot(client);
            if (emptyHotbar < 0) {
                return;
            }
            InventoryUtil.moveToHotbar(client, backpack, emptyHotbar);
            ticks = 0;
            return;
        }
        InventoryUtil.swapOffhand(client, slot);
        ticks = 0;
    }

    private static int emptyHotbarSlot(MinecraftClient client) {
        for (int slot = 0; slot < 9; slot++) {
            if (InventoryUtil.stackAt(client, slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static Item itemFor(Target target) {
        return switch (target) {
            case TOTEM -> Items.TOTEM_OF_UNDYING;
            case CRYSTAL -> Items.END_CRYSTAL;
            case GAPPLE -> Items.GOLDEN_APPLE;
            case SHIELD -> Items.SHIELD;
        };
    }
}
