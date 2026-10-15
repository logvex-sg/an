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
 * WebAura: places cobwebs on a target's feet to slow them, a common crystal-PvP control move.
 * Places through the vanilla interaction manager, one web per interval.
 *
 * <p>Only places into a replaceable position in reach, so it never overwrites a block.
 */
public final class WebAuraModule extends Module {

    private final IntSetting range = add("Range", "Maximum distance to the target.", 5, 2, 8);
    private final BooleanSetting onFeet = add("OnFeet", "Place a web at the target's feet.", true);
    private final BooleanSetting onHead = add("OnHead", "Also place a web at the target's head.", false);
    private final IntSetting interval = add("Interval", "Minimum ticks between placements.", 5, 1, 40);

    private int ticks;

    public WebAuraModule() {
        super(Category.COMBAT, "WebAura", "Places cobwebs on a target to slow them.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null) {
            return;
        }
        var target = context().combatService().currentTarget();
        if (target == null || target.distance() > range.value()) {
            return;
        }
        if (InventoryUtil.findInHotbar(client, Items.COBWEB) < 0) {
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        BlockPos feet = target.entity().getBlockPos();
        CrystalPlanner.Pos feetPos = new CrystalPlanner.Pos(feet.getX(), feet.getY(), feet.getZ());
        for (CrystalPlanner.Pos pos : CrystalPlanner.webPositions(feetPos, onHead.value())) {
            if (pos.equals(feetPos) && !onFeet.value()) {
                continue;
            }
            BlockPos targetPos = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!WorldInteract.isReplaceable(client, targetPos) || !WorldInteract.inReach(client, targetPos)) {
                continue;
            }
            WorldInteract.lookAt(client, Vec3d.ofCenter(targetPos));
            if (WorldInteract.placeAt(client, targetPos, Items.COBWEB)) {
                return;
            }
        }
    }
}
