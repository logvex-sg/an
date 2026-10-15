package com.logvex.crystal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A bounded, loaded-chunks-only block search.
 *
 * <p>Modules that highlight specific blocks (beds, portals, a searched block type) all need
 * the same walk: "in a cube around the player, find blocks matching this predicate". Doing
 * it here keeps the cost predictable and identical everywhere, and makes the "never scan
 * unloaded chunks, never exceed the cap" rules a property of one method rather than a
 * promise each module has to keep.
 *
 * <p>The scan skips whole chunks that are not loaded, so it cannot pull data the client does
 * not have, and it stops as soon as {@code max} matches are found.
 */
public final class BlockScanner {

    private BlockScanner() {
    }

    /**
     * Finds up to {@code max} block positions matching {@code match} within a cube of
     * {@code radius} blocks horizontally and {@code verticalRadius} vertically around
     * {@code center}.
     */
    public static List<BlockPos> find(World world, BlockPos center, int radius, int verticalRadius,
                                      int max, Predicate<BlockState> match) {
        List<BlockPos> results = new ArrayList<>();
        if (world == null || max <= 0) {
            return results;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int minY = Math.max(world.getBottomY(), center.getY() - verticalRadius);
        int maxY = Math.min(world.getTopYInclusive(), center.getY() + verticalRadius);
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean chunkLoaded = false;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    chunkLoaded = world.isChunkLoaded(chunkX, chunkZ);
                }
                if (!chunkLoaded) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = world.getBlockState(pos.set(x, y, z));
                    if (match.test(state)) {
                        results.add(pos.toImmutable());
                        if (results.size() >= max) {
                            return results;
                        }
                    }
                }
            }
        }
        return results;
    }
}
