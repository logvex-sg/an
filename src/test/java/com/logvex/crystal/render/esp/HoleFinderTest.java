package com.logvex.crystal.render.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the hole classification. The finder is Minecraft-free by design, so a
 * {@link HoleFinder.BlockGrid} backed by a set of coordinates reproduces any world shape
 * exactly and the classification can be pinned without a running client.
 */
class HoleFinderTest {

    /** A tiny grid of solid blocks with per-block blast resistance. */
    private static final class TestGrid implements HoleFinder.BlockGrid {
        private final Set<Long> solid = new HashSet<>();
        private final Set<Long> air = new HashSet<>();
        private final java.util.Map<Long, Float> resistance = new java.util.HashMap<>();
        private float defaultResistance = 10f;

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
        }

        void fill(int x, int y, int z) {
            solid.add(key(x, y, z));
            air.remove(key(x, y, z));
        }

        void fill(int x, int y, int z, float resistanceValue) {
            fill(x, y, z);
            resistance.put(key(x, y, z), resistanceValue);
        }

        void clear(int x, int y, int z) {
            air.add(key(x, y, z));
            solid.remove(key(x, y, z));
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return air.contains(key(x, y, z)) || !solid.contains(key(x, y, z));
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return solid.contains(key(x, y, z));
        }

        @Override
        public float blastResistance(int x, int y, int z) {
            if (!solid.contains(key(x, y, z))) {
                return 0f;
            }
            return resistance.getOrDefault(key(x, y, z), defaultResistance);
        }
    }

    /** Builds a 1x2 hole at the origin with all four sides and the floor filled. */
    private static TestGrid hole(float resistance) {
        TestGrid grid = new TestGrid();
        grid.defaultResistance = resistance;
        grid.clear(0, 0, 0);
        grid.clear(0, 1, 0);
        grid.fill(0, -1, 0);
        grid.fill(1, 0, 0);
        grid.fill(-1, 0, 0);
        grid.fill(0, 0, 1);
        grid.fill(0, 0, -1);
        grid.fill(1, 1, 0);
        grid.fill(-1, 1, 0);
        grid.fill(0, 1, 1);
        grid.fill(0, 1, -1);
        return grid;
    }

    @Test
    void bedrockHoleIsClassifiedBedrock() {
        TestGrid grid = hole(HoleFinder.UNBREAKABLE);
        assertEquals(HoleFinder.HoleType.BEDROCK, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void obsidianHoleIsClassifiedObsidian() {
        TestGrid grid = hole(HoleFinder.BLAST_RESISTANT);
        assertEquals(HoleFinder.HoleType.OBSIDIAN, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void softSidedHoleIsSingle() {
        TestGrid grid = hole(10f);
        assertEquals(HoleFinder.HoleType.SINGLE, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void mixedSidesAreClassifiedMixed() {
        TestGrid grid = hole(10f);
        // Two of the four feet-level sides become obsidian.
        grid.fill(1, 0, 0, HoleFinder.BLAST_RESISTANT);
        grid.fill(0, 0, 1, HoleFinder.BLAST_RESISTANT);
        assertEquals(HoleFinder.HoleType.MIXED, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void missingCeilingHeadroomIsNotAHole() {
        TestGrid grid = hole(HoleFinder.UNBREAKABLE);
        grid.fill(0, 1, 0); // head is blocked
        assertEquals(HoleFinder.HoleType.NONE, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void missingFloorIsNotAHole() {
        TestGrid grid = hole(HoleFinder.UNBREAKABLE);
        grid.clear(0, -1, 0); // floor gone
        assertEquals(HoleFinder.HoleType.NONE, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void openSideIsNotAHole() {
        TestGrid grid = hole(HoleFinder.UNBREAKABLE);
        grid.clear(1, 0, 0); // one side open
        assertEquals(HoleFinder.HoleType.NONE, HoleFinder.classify(grid, 0, 0, 0));
    }

    @Test
    void weakestSideReportsLowestResistance() {
        TestGrid grid = hole(HoleFinder.BLAST_RESISTANT);
        grid.fill(0, 0, 1, 5f);
        assertEquals(5f, HoleFinder.weakestSide(grid, 0, 0, 0), 1e-6);
    }

    @Test
    void findReturnsTheHoleWithinRange() {
        TestGrid grid = hole(HoleFinder.UNBREAKABLE);
        var holes = HoleFinder.find(grid, 0, 0, 0, 3);
        assertTrue(holes.stream().anyMatch(h -> h.x() == 0 && h.y() == 0 && h.z() == 0));
        assertEquals(HoleFinder.HoleType.BEDROCK, holes.get(0).type());
    }

    @Test
    void findRespectsRadius() {
        TestGrid grid = new TestGrid();
        grid.defaultResistance = HoleFinder.UNBREAKABLE;
        // A hole far away must not be reported for a small radius.
        int far = 6;
        grid.clear(far, 0, 0);
        grid.clear(far, 1, 0);
        grid.fill(far, -1, 0);
        grid.fill(far + 1, 0, 0);
        grid.fill(far - 1, 0, 0);
        grid.fill(far, 0, 1);
        grid.fill(far, 0, -1);
        grid.fill(far + 1, 1, 0);
        grid.fill(far - 1, 1, 0);
        grid.fill(far, 1, 1);
        grid.fill(far, 1, -1);
        assertTrue(HoleFinder.find(grid, 0, 0, 0, 2).isEmpty());
        assertTrue(HoleFinder.find(grid, 0, 0, 0, 8).stream().anyMatch(h -> h.x() == far));
    }
}
