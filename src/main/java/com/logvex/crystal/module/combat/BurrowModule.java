package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.CrystalPlanner;
import com.logvex.crystal.combat.WorldInteract;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Burrow: places a block in the player's own space (the standard anarchy escape), choosing
 * obsidian when held and falling back to a portable block such as a shulker box.
 *
 * <p>Placement goes through the vanilla interaction manager. It looks for a solid neighbour to
 * click against, so the block lands in the player's feet without overwriting anything.
 */
public final class BurrowModule extends Module {

    private final IntSetting interval = add("Interval", "Minimum ticks between attempts.", 2, 1, 20);

    private int ticks;

    public BurrowModule() {
        super(Category.COMBAT, "Burrow", "Places a block in your own space to escape.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null) {
            return;
        }
        BlockPos feet = client.player.getBlockPos();
        if (!client.world.getBlockState(feet).isReplaceable()) {
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        Item block = chooseBlock(client);
        if (block == null) {
            return;
        }
        WorldInteract.lookAt(client, Vec3d.ofCenter(feet));
        WorldInteract.placeAt(client, feet, block);
    }

    private Item chooseBlock(MinecraftClient client) {
        if (InventoryUtil.findInHotbar(client, Items.OBSIDIAN) >= 0) {
            return Items.OBSIDIAN;
        }
        for (Item candidate : new Item[]{Items.SHULKER_BOX, Items.ENDER_CHEST, Items.CRYING_OBSIDIAN}) {
            if (InventoryUtil.findInHotbar(client, candidate) >= 0) {
                return candidate;
            }
        }
        return null;
    }
}
