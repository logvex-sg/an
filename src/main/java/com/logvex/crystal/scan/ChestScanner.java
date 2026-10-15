package com.logvex.crystal.scan;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Reads chest positions out of the chunks the client already has loaded.
 *
 * <p>It walks only loaded chunk sections around the player and only counts the two block
 * entities the feature cares about: chests and trapped chests. Ender chests are excluded —
 * they are per-player and hold no shared loot, so they are not a base indicator. Block
 * contents are never read; only the block entity type is inspected.
 *
 * <p>Chunk forcing, unloaded-chunk access and any server-protection bypass are deliberately
 * absent: a chunk that is not loaded simply contributes nothing.
 */
public final class ChestScanner {

    private ChestScanner() {
    }

    /** The loaded-chunk radius around the player that is scanned, in chunks. */
    public static final int DEFAULT_CHUNK_RADIUS = 6;

    /**
     * Scans the loaded chunks within {@code chunkRadius} of the player and returns every
     * chest / trapped-chest position found. Never touches an unloaded chunk.
     */
    public static List<ChestPoint> scan(ClientWorld world, int centerChunkX, int centerChunkZ, int chunkRadius) {
        List<ChestPoint> points = new ArrayList<>();
        if (world == null) {
            return points;
        }
        int radius = Math.max(0, chunkRadius);
        for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
            for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                collectChunk(chunk, points);
            }
        }
        return points;
    }

    private static void collectChunk(WorldChunk chunk, List<ChestPoint> out) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (!isCounted(entity)) {
                continue;
            }
            BlockPos pos = entity.getPos();
            out.add(new ChestPoint(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /** True only for chests and trapped chests. Ender chests are intentionally excluded. */
    public static boolean isCounted(BlockEntity entity) {
        if (entity instanceof EnderChestBlockEntity) {
            return false;
        }
        BlockEntityType<?> type = entity.getType();
        return type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST
                || entity instanceof ChestBlockEntity;
    }

    /**
     * The names of players the client can already see, within {@code radius} blocks of the
     * cluster centre. Only names present in the client's own loaded world are returned —
     * exactly the set that is already on screen. Returns an empty list when nobody is near.
     */
    public static List<String> nearbyPlayers(ClientWorld world, int x, int y, int z, double radius) {
        List<String> names = new ArrayList<>();
        if (world == null) {
            return names;
        }
        double radiusSquared = radius * radius;
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player == null || player.getGameProfile() == null) {
                continue;
            }
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                names.add(player.getNameForScoreboard());
            }
        }
        return names;
    }

    /** The chunk the position sits in, as a {@link ChunkPos}. */
    public static ChunkPos chunkOf(int blockX, int blockZ) {
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }
}
