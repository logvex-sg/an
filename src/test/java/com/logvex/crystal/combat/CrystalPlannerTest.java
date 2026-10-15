package com.logvex.crystal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the shared surround/city geometry so a module and its tests agree on which blocks are
 * involved. Pure integer maths, no Minecraft runtime needed.
 */
class CrystalPlannerTest {

    private static final CrystalPlanner.Pos FEET = new CrystalPlanner.Pos(10, 64, -5);

    @Test
    void surroundHasFourFaces() {
        List<CrystalPlanner.Pos> positions = CrystalPlanner.surroundPositions(FEET, false);
        assertEquals(4, positions.size());
        assertTrue(positions.contains(FEET.offset(1, 0, 0)));
        assertTrue(positions.contains(FEET.offset(-1, 0, 0)));
        assertTrue(positions.contains(FEET.offset(0, 0, 1)));
        assertTrue(positions.contains(FEET.offset(0, 0, -1)));
        assertFalse(positions.contains(FEET));
    }

    @Test
    void surroundCornersAreOneLevelDownDiagonals() {
        List<CrystalPlanner.Pos> positions = CrystalPlanner.surroundPositions(FEET, true);
        assertEquals(8, positions.size());
        assertTrue(positions.contains(FEET.offset(1, -1, 1)));
        assertTrue(positions.contains(FEET.offset(-1, -1, -1)));
    }

    @Test
    void burrowIsTheFeetPosition() {
        assertEquals(FEET, CrystalPlanner.burrowPosition(FEET));
    }

    @Test
    void cityTargetsTheFourHorizontalNeighbours() {
        List<CrystalPlanner.Pos> positions = CrystalPlanner.cityPositions(FEET);
        assertEquals(4, positions.size());
        assertTrue(positions.contains(FEET.offset(0, 0, 1)));
        assertFalse(positions.contains(FEET));
    }

    @Test
    void webIncludesHeadOnlyWhenAsked() {
        assertEquals(1, CrystalPlanner.webPositions(FEET, false).size());
        List<CrystalPlanner.Pos> withHead = CrystalPlanner.webPositions(FEET, true);
        assertEquals(2, withHead.size());
        assertTrue(withHead.contains(FEET.offset(0, 1, 0)));
    }
}
