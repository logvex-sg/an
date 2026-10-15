package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.CrystalPlanner;
import com.logvex.crystal.combat.WorldInteract;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.player.InventoryUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Surround: walls the player in with obsidian before a crystal fight, placing the four blocks
 * at feet level (and optionally the corner blocks one level down) that stop a crystal being
 * placed against the player.
 *
 * <p>It places through the vanilla interaction manager and only into replaceable positions in
 * reach; it never overwrites an existing block.
 */
public final class SurroundModule extends Module {

    private final IntSetting blocks = add("Blocks", "How many surround blocks to place per tick.", 1, 1, 4);
    private final BooleanSetting corners = add("Corners", "Also block the lower corners.", true);
    private final IntSetting interval = add("Interval", "Minimum ticks between placements.", 1, 1, 10);

    private int ticks;

    public SurroundModule() {
        super(Category.COMBAT, "Surround", "Walls the player in with obsidian against crystals.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null) {
            return;
        }
        if (InventoryUtil.findInHotbar(client, Items.OBSIDIAN) < 0) {
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        BlockPos feet = client.player.getBlockPos();
        var positions = CrystalPlanner.surroundPositions(
                new CrystalPlanner.Pos(feet.getX(), feet.getY(), feet.getZ()), corners.value());
        int placed = 0;
        for (CrystalPlanner.Pos pos : positions) {
            if (placed >= blocks.value()) {
                return;
            }
            BlockPos target = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!WorldInteract.isReplaceable(client, target) || !WorldInteract.inReach(client, target)) {
                continue;
            }
            WorldInteract.lookAt(client, Vec3d.ofCenter(target));
            if (WorldInteract.placeAt(client, target, Items.OBSIDIAN)) {
                placed++;
            }
        }
    }
}
