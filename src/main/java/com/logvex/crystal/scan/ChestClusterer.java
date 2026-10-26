package com.logvex.crystal.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups chest positions into clusters, without any Minecraft dependency.
 *
 * <p>Two chests belong to the same cluster when they are within {@code linkDistance} blocks
 * of each other on the XZ plane and {@code verticalTolerance} blocks vertically. Grouping
 * is transitive (a connected component), so a long row of chests becomes one cluster rather
 * than several.
 *
 * <p>The algorithm is a union-find over the input points. Points are first bucketed into a
 * coarse grid so only neighbouring buckets are compared, which keeps the cost near-linear
 * even for a large haul; a naive all-pairs compare would be quadratic and could stutter on
 * a big base.
 */
public final class ChestClusterer {

    private ChestClusterer() {
    }

    /**
     * Clusters the given points and returns the clusters that contain at least
     * {@code minSize} chests, largest first.
     *
     * @param points           chest positions (any order, duplicates tolerated)
     * @param linkDistance     max horizontal gap between two chests in the same cluster
     * @param verticalTolerance max vertical gap between two chests in the same cluster
     * @param minSize          clusters smaller than this are dropped
     */
    public static List<ChestCluster> cluster(List<ChestPoint> points, int linkDistance,
                                             int verticalTolerance, int minSize) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        int link = Math.max(0, linkDistance);
        int vertical = Math.max(0, verticalTolerance);
        // The cell must cover the larger of the two tolerances: the 27-cell neighbour scan
        // only reaches one cell in each direction, so a cell sized from the horizontal link
        // alone would miss two chests that are close vertically but further apart on Y than
        // one cell (e.g. a tall stack with a generous vertical tolerance).
        int cell = Math.max(1, Math.max(link, vertical) + 1);

        List<ChestPoint> unique = dedupe(points);
        int n = unique.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }

        // Bucket by (x, y, z) cell. Compare each point only against points in the 27
        // neighbouring cells, which is enough to catch anything within the link distance.
        java.util.Map<Cell, List<Integer>> buckets = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            buckets.computeIfAbsent(cellOf(unique.get(i), cell), k -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < n; i++) {
            ChestPoint a = unique.get(i);
            int bx = Math.floorDiv(a.x(), cell);
            int by = Math.floorDiv(a.y(), cell);
            int bz = Math.floorDiv(a.z(), cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> bucket = buckets.get(new Cell(bx + dx, by + dy, bz + dz));
                        if (bucket == null) {
                            continue;
                        }
                        for (int j : bucket) {
                            if (j <= i) {
                                continue;
                            }
                            if (withinLink(a, unique.get(j), link, vertical)) {
                                union(parent, i, j);
                            }
                        }
                    }
                }
            }
        }

        // Gather members per root.
        java.util.Map<Integer, List<ChestPoint>> groups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(unique.get(i));
        }

        List<ChestCluster> result = new ArrayList<>();
        for (List<ChestPoint> group : groups.values()) {
            if (group.size() >= Math.max(1, minSize)) {
                result.add(summarise(group));
            }
        }
        result.sort((a, b) -> Integer.compare(b.chestCount(), a.chestCount()));
        return result;
    }

    /** Convenience overload using a single link distance for all axes. */
    public static List<ChestCluster> cluster(List<ChestPoint> points, int linkDistance, int minSize) {
        return cluster(points, linkDistance, linkDistance, minSize);
    }

    private static boolean withinLink(ChestPoint a, ChestPoint b, int link, int vertical) {
        if (Math.abs(a.y() - b.y()) > vertical) {
            return false;
        }
        long dx = a.x() - b.x();
        long dz = a.z() - b.z();
        return dx * dx + dz * dz <= (long) link * link;
    }

    private static ChestCluster summarise(List<ChestPoint> group) {
        long sx = 0;
        long sy = 0;
        long sz = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ChestPoint p : group) {
            sx += p.x();
            sy += p.y();
            sz += p.z();
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
        }
        int size = group.size();
        return new ChestCluster(Math.round((float) sx / size), Math.round((float) sy / size),
                Math.round((float) sz / size), size, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<ChestPoint> dedupe(List<ChestPoint> points) {
        Set<ChestPoint> seen = new HashSet<>(points);
        return new ArrayList<>(seen);
    }

    /** An integer cell coordinate, used as a map key without boxing to a string. */
    private record Cell(int x, int y, int z) {
    }

    private static Cell cellOf(ChestPoint p, int cell) {
        return new Cell(Math.floorDiv(p.x(), cell), Math.floorDiv(p.y(), cell), Math.floorDiv(p.z(), cell));
    }

    private static int find(int[] parent, int i) {
        int root = i;
        while (parent[root] != root) {
            root = parent[root];
        }
        // Path compression.
        while (parent[i] != root) {
            int next = parent[i];
            parent[i] = root;
            i = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }
}
