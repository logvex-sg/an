package com.logvex.crystal.scan;

/**
 * A group of chest blocks that sit close together, summarised by its centre and count.
 *
 * <p>{@code centerX/Y/Z} is the rounded average of the member positions; the bounds are the
 * cluster's extent. Everything here is plain data so it can be produced and asserted
 * without a client.
 */
public record ChestCluster(int centerX, int centerY, int centerZ, int chestCount,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Widest horizontal span of the cluster, in blocks. */
    public int spanX() {
        return maxX - minX + 1;
    }

    /** Widest vertical span of the cluster, in blocks. */
    public int spanY() {
        return maxY - minY + 1;
    }

    public int spanZ() {
        return maxZ - minZ + 1;
    }

    /**
     * A coarse, stable identifier for the cluster: its centre quantised to a grid.
     *
     * <p>Used for logging and for the first-line duplicate check. A cluster whose centre
     * drifts by more than half a cell (for example as edge chunks load) is caught by the
     * proximity check in {@link ClusterReporter} instead.
     */
    public String id(String dimension, int cellSize) {
        int cell = Math.max(1, cellSize);
        return dimension + "@" + Math.floorDiv(centerX, cell) + "," + Math.floorDiv(centerY, cell)
                + "," + Math.floorDiv(centerZ, cell);
    }
}
