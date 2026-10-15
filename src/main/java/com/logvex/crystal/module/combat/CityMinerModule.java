package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.CrystalPlanner;
import com.logvex.crystal.combat.WorldInteract;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * CityMiner: breaks the blocks around a trapped target so they can be hit directly, mining the
 * closest reachable breakable neighbour of the target's feet.
 *
 * <p>It calls the vanilla block-breaking action, which the server validates for reach and tool
 * exactly as for a held left click, and only mines blocks the client can already see.
 */
public final class CityMinerModule extends Module {

    private final IntSetting range = add("Range", "Maximum distance to the target.", 5, 2, 8);
    private final BooleanSetting onlySoft = add("OnlySoft", "Skip obsidian and similarly hard blocks.", true);
    private final IntSetting interval = add("Interval", "Minimum ticks between mining actions.", 2, 1, 20);

    private int ticks;

    public CityMinerModule() {
        super(Category.COMBAT, "CityMiner", "Mines the blocks trapping a target.");
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
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        BlockPos feet = target.entity().getBlockPos();
        for (CrystalPlanner.Pos pos : CrystalPlanner.cityPositions(
                new CrystalPlanner.Pos(feet.getX(), feet.getY(), feet.getZ()))) {
            BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!breakable(client, block)) {
                continue;
            }
            Direction face = Direction.getFacing(client.player.getEyePos().subtract(Vec3d.ofCenter(block)));
            WorldInteract.mine(client, block, face);
            return;
        }
    }

    @Override
    protected void onDisable() {
        WorldInteract.cancelMining(MinecraftClient.getInstance());
    }

    private boolean breakable(MinecraftClient client, BlockPos pos) {
        var state = client.world.getBlockState(pos);
        if (state.isAir() || state.isReplaceable() || !WorldInteract.inReach(client, pos)) {
            return false;
        }
        float hardness = state.getHardness(client.world, pos);
        if (hardness < 0) {
            return false;
        }
        return !onlySoft.value() || hardness <= 5.0f;
    }
}
