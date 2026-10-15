package com.logvex.crystal.module.combat;

import com.logvex.crystal.combat.WorldInteract;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * AutoMine: automatically mines the blocks around a target so a crystal can be placed against
 * them, or so they can be hit directly. It drives the vanilla block-breaking progress, which
 * the server validates for reach and tool exactly as for a held left click.
 *
 * <p>It only ever mines blocks the client can already see and reach, and never forces chunks
 * to load.
 */
public final class AutoMineModule extends Module {

    private final IntSetting range = add("Range", "Maximum distance to a block to mine.", 4, 2, 6);
    private final IntSetting targetRange = add("TargetRange", "Only act on targets within this range.", 6, 2, 10);
    private final BooleanSetting onlySoft = add("OnlySoft", "Skip obsidian and similarly hard blocks.", true);
    private final IntSetting interval = add("Interval", "Minimum ticks between mining actions.", 2, 1, 20);

    private int ticks;

    public AutoMineModule() {
        super(Category.COMBAT, "AutoMine", "Mines blocks around a target to open them up.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null
                || client.currentScreen != null) {
            return;
        }
        var target = context().combatService().currentTarget();
        if (target == null || target.distance() > targetRange.value()) {
            return;
        }
        if (++ticks < interval.value()) {
            return;
        }
        ticks = 0;
        BlockPos best = findBlock(client, target.entity().getBlockPos());
        if (best == null) {
            return;
        }
        Vec3d from = client.player.getEyePos();
        Direction face = Direction.getFacing(from.subtract(Vec3d.ofCenter(best)));
        WorldInteract.mine(client, best, face);
    }

    @Override
    protected void onDisable() {
        WorldInteract.cancelMining(MinecraftClient.getInstance());
    }

    private BlockPos findBlock(MinecraftClient client, BlockPos targetFeet) {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(targetFeet.up());
        for (BlockPos pos : List.of(targetFeet.north(), targetFeet.south(), targetFeet.east(), targetFeet.west())) {
            candidates.add(pos);
            candidates.add(pos.up());
        }
        candidates.sort(Comparator.comparingDouble(p -> client.player.getEntityPos().squaredDistanceTo(
                Vec3d.ofCenter(p))));
        for (BlockPos pos : candidates) {
            if (!WorldInteract.inReach(client, pos)) {
                continue;
            }
            if (client.player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(pos)) > (double) range.value() * range.value()) {
                continue;
            }
            var state = client.world.getBlockState(pos);
            if (state.isAir() || state.isReplaceable()) {
                continue;
            }
            float hardness = state.getHardness(client.world, pos);
            if (hardness < 0) {
                continue;
            }
            if (onlySoft.value() && hardness > 5.0f) {
                continue;
            }
            return pos;
        }
        return null;
    }
}
