package com.logvex.crystal.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for the crystal-PvP automation modules: where to place obsidian for a
 * surround, where a burrow block goes, and which blocks to break around a trapped target.
 *
 * <p>This is deliberately free of any Minecraft types so it can be unit tested without a
 * client, and so every module shares one definition of "the blocks around a player" instead
 * of re-deriving offsets. Coordinates are plain block coordinates; modules convert to and
 * from {@code BlockPos}.
 */
public final class CrystalPlanner {

    /** An immutable integer block coordinate. */
    public record Pos(int x, int y, int z) {

        /** This position offset by the given deltas. */
        public Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    /** The four horizontal neighbours, in a stable order. */
    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** The four diagonals, used for the extended surround's corner blocks. */
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private CrystalPlanner() {
    }

    /**
     * The obsidian positions for a surround of a player standing at {@code feet}: the four
     * blocks at feet level, and — when {@code includeCorners} is set — the four diagonal
     * blocks one level down that stop a crystal being placed underneath the corners. The
     * caller still checks each position is replaceable and in reach.
     */
    public static List<Pos> surroundPositions(Pos feet, boolean includeCorners) {
        List<Pos> positions = new ArrayList<>();
        for (int[] d : HORIZONTAL) {
            positions.add(feet.offset(d[0], 0, d[1]));
        }
        if (includeCorners) {
            for (int[] d : DIAGONAL) {
                positions.add(feet.offset(d[0], -1, d[1]));
            }
        }
        return positions;
    }

    /** The block a burrow places into: the space the player is standing in. */
    public static Pos burrowPosition(Pos feet) {
        return feet;
    }

    /** The four horizontal neighbours of a trapped target's feet, in a stable order. */
    public static List<Pos> cityPositions(Pos targetFeet) {
        List<Pos> positions = new ArrayList<>();
        for (int[] d : HORIZONTAL) {
            positions.add(targetFeet.offset(d[0], 0, d[1]));
        }
        return positions;
    }

    /** The blocks a web placement targets: the target's feet, and optionally their head. */
    public static List<Pos> webPositions(Pos targetFeet, boolean includeHead) {
        List<Pos> positions = new ArrayList<>();
        positions.add(targetFeet);
        if (includeHead) {
            positions.add(targetFeet.offset(0, 1, 0));
        }
        return positions;
    }
}
