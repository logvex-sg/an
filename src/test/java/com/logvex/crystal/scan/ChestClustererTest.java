package com.logvex.crystal.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests chest counting and cluster detection: grouping by proximity, transitive linking,
 * the size filter, and the centre/bounds of a cluster.
 */
class ChestClustererTest {

    private static ChestPoint p(int x, int y, int z) {
        return new ChestPoint(x, y, z);
    }

    @Test
    void adjacentChestsFormOneCluster() {
        List<ChestPoint> points = List.of(p(0, 64, 0), p(1, 64, 0), p(2, 64, 0));
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(1, clusters.size());
        assertEquals(3, clusters.get(0).chestCount());
        assertEquals(1, clusters.get(0).centerX());
        assertEquals(0, clusters.get(0).minX());
        assertEquals(2, clusters.get(0).maxX());
    }

    @Test
    void distantChestsAreSeparateClusters() {
        List<ChestPoint> points = List.of(p(0, 64, 0), p(1, 64, 0), p(100, 64, 100), p(101, 64, 100));
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(2, clusters.size());
        assertTrue(clusters.stream().allMatch(c -> c.chestCount() == 2));
    }

    @Test
    void linkingIsTransitiveAcrossAChain() {
        // A 5-long row, each step 1 block apart, must become a single cluster.
        List<ChestPoint> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(p(i, 64, 0));
        }
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(1, clusters.size());
        assertEquals(5, clusters.get(0).chestCount());
    }

    @Test
    void verticalToleranceSeparatesFloors() {
        // Same XZ but 5 blocks apart vertically: outside a tolerance of 1, so two clusters.
        List<ChestPoint> points = List.of(p(0, 64, 0), p(0, 64, 1), p(0, 70, 0), p(0, 70, 1));
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(2, clusters.size());
    }

    @Test
    void clustersBelowMinSizeAreDropped() {
        List<ChestPoint> points = List.of(p(0, 64, 0), p(1, 64, 0), p(500, 64, 500));
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).chestCount());
    }

    @Test
    void duplicatePositionsCountOnce() {
        List<ChestPoint> points = List.of(p(0, 64, 0), p(0, 64, 0), p(0, 64, 0), p(1, 64, 0));
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(1, clusters.size());
        assertEquals(2, clusters.get(0).chestCount(), "a repeated position is a single chest");
    }

    @Test
    void aThirtyChestHaulFormsOneCluster() {
        // Two rows of 15 chests, packed 1 apart: the canonical "big stash" shape.
        List<ChestPoint> points = new ArrayList<>();
        for (int x = 0; x < 15; x++) {
            points.add(p(x, 64, 0));
            points.add(p(x, 64, 1));
        }
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(1, clusters.size());
        assertEquals(30, clusters.get(0).chestCount());
    }

    @Test
    void emptyInputYieldsNoClusters() {
        assertTrue(ChestClusterer.cluster(List.of(), 2, 1, 2).isEmpty());
        assertTrue(ChestClusterer.cluster(null, 2, 1, 2).isEmpty());
    }

    @Test
    void clustersAreSortedLargestFirst() {
        List<ChestPoint> points = new ArrayList<>();
        // Small cluster of 2 at the origin.
        points.add(p(0, 64, 0));
        points.add(p(1, 64, 0));
        // Larger cluster of 4 far away.
        for (int i = 0; i < 4; i++) {
            points.add(p(200 + i, 64, 200));
        }
        List<ChestCluster> clusters = ChestClusterer.cluster(points, 2, 1, 2);
        assertEquals(2, clusters.size());
        assertEquals(4, clusters.get(0).chestCount());
        assertEquals(2, clusters.get(1).chestCount());
    }

    @Test
    void clusterIdIsStableAndQuantised() {
        ChestCluster cluster = new ChestCluster(35, 64, -3, 40, 30, 63, -5, 40, 65, -1);
        String id = cluster.id("minecraft:overworld", 16);
        assertEquals("minecraft:overworld@2,4,-1", id);
        // Same cluster produces the same id.
        assertEquals(id, cluster.id("minecraft:overworld", 16));
    }
}
