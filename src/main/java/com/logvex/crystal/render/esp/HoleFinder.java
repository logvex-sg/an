package com.logvex.crystal.render.esp;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies "holes" — 1x2 safe pockets an anarchy player can stand in — from the loaded
 * world, without scanning the whole world.
 *
 * <p>The scan is bounded to a radius around the player and only looks at the loaded blocks
 * the caller can answer for, so the cost is O(radius^3) with a tiny constant, not a chunk
 * sweep. The logic is deliberately Minecraft-free: the caller supplies a
 * {@link BlockGrid}, which keeps the classification unit-testable and makes the module a
 * thin adapter over world state.
 *
 * <p>A valid hole is: air at feet and head, a solid floor below, and all four horizontal
 * sides solid. The type is decided by the weakest side, because that is what an opponent
 * would mine through.
 */
public final class HoleFinder {

    /** Blast resistance at or above this is treated as unbreakable (bedrock, barrier). */
    public static final float UNBREAKABLE = 3_600_000f;
    /** Blast resistance at or above this is treated as blast-resistant (obsidian, debris). */
    public static final float BLAST_RESISTANT = 1000f;

    private HoleFinder() {
    }

    /** The kind of protection a hole offers. */
    public enum HoleType {
        /** All sides are unbreakable: the strongest possible hole. */
        BEDROCK,
        /** All sides are blast-resistant but breakable. */
        OBSIDIAN,
        /** A mix of resistant and soft sides. */
        MIXED,
        /** A single soft side only. */
        SINGLE,
        /** Not a hole. */
        NONE
    }

    /** A classified hole. */
    public record Hole(int x, int y, int z, HoleType type, float weakestSide) {
    }

    /** Read-only view of the world the finder needs. Implemented over a live ClientWorld. */
    public interface BlockGrid {
        boolean isAir(int x, int y, int z);

        boolean isSolid(int x, int y, int z);

        /** Blast resistance of the block at the position; 0 when unknown/air. */
        float blastResistance(int x, int y, int z);
    }

    /**
     * Finds every hole whose feet block lies within a cube of {@code radius} around the
     * centre. The scan walks the horizontal plane of the player's feet plus one block up
     * and down, which is where holes occur, rather than the full cube.
     */
    public static List<Hole> find(BlockGrid grid, int centerX, int centerY, int centerZ, int radius) {
        List<Hole> holes = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    HoleType type = classify(grid, x, y, z);
                    if (type != HoleType.NONE) {
                        holes.add(new Hole(x, y, z, type, weakestSide(grid, x, y, z)));
                    }
                }
            }
        }
        return holes;
    }

    /** Classifies the block position as a hole of some type, or {@link HoleType#NONE}. */
    public static HoleType classify(BlockGrid grid, int x, int y, int z) {
        if (!isValidHole(grid, x, y, z)) {
            return HoleType.NONE;
        }
        int unbreakable = 0;
        int resistant = 0;
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            float r = grid.blastResistance(x + dir[0], y, z + dir[1]);
            if (r >= UNBREAKABLE) {
                unbreakable++;
            } else if (r >= BLAST_RESISTANT) {
                resistant++;
            }
        }
        if (unbreakable == 4) {
            return HoleType.BEDROCK;
        }
        if (unbreakable + resistant == 4) {
            return HoleType.OBSIDIAN;
        }
        if (unbreakable + resistant >= 2) {
            return HoleType.MIXED;
        }
        return HoleType.SINGLE;
    }

    /** The blast resistance of the weakest of the four sides. */
    public static float weakestSide(BlockGrid grid, int x, int y, int z) {
        float weakest = Float.MAX_VALUE;
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            weakest = Math.min(weakest, grid.blastResistance(x + dir[0], y, z + dir[1]));
        }
        return weakest == Float.MAX_VALUE ? 0f : weakest;
    }

    private static boolean isValidHole(BlockGrid grid, int x, int y, int z) {
        if (!grid.isAir(x, y, z) || !grid.isAir(x, y + 1, z)) {
            return false;
        }
        if (!grid.isSolid(x, y - 1, z)) {
            return false;
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!grid.isSolid(x + dir[0], y, z + dir[1]) || !grid.isSolid(x + dir[0], y + 1, z + dir[1])) {
                return false;
            }
        }
        return true;
    }
}
