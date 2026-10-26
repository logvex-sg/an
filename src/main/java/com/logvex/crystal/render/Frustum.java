package com.logvex.crystal.render;

/**
 * A view frustum extracted from a combined view-projection matrix, used to reject ESP
 * primitives that cannot appear on screen before any vertex work is done.
 *
 * <p>Culling here is a pure geometric decision over an axis-aligned box, so it is fully
 * unit-testable without a GL context and identical for every ESP provider. A box that is
 * entirely outside any one of the six clip planes is skipped; a box that straddles a plane
 * is kept (a conservative test), so a partially visible entity is never wrongly hidden.
 *
 * <p>The matrix layout matches {@link ScreenProjector}/{@link ViewProjection}: column-major
 * with element {@code (row, col)} at index {@code col * 4 + row}, which is what OpenGL and
 * JOML produce.
 */
public final class Frustum {

    // Plane coefficients (a, b, c, d) for ax + by + cz + d = 0, in world space.
    private final float[] left = new float[4];
    private final float[] right = new float[4];
    private final float[] bottom = new float[4];
    private final float[] top = new float[4];
    private final float[] near = new float[4];
    private final float[] far = new float[4];

    private Frustum() {
    }

    /**
     * Extracts the six clip planes from a column-major view-projection matrix.
     *
     * @param mvp column-major 4x4 view-projection matrix (as produced by
     *            {@link ViewProjection#build})
     */
    public static Frustum fromMatrix(float[] mvp) {
        if (mvp == null || mvp.length < 16) {
            throw new IllegalArgumentException("mvp must be a 16-element matrix");
        }
        Frustum f = new Frustum();
        // Row i of the column-major matrix is {m[i], m[4+i], m[8+i], m[12+i]}.
        float[] row0 = {mvp[0], mvp[4], mvp[8], mvp[12]};
        float[] row1 = {mvp[1], mvp[5], mvp[9], mvp[13]};
        float[] row2 = {mvp[2], mvp[6], mvp[10], mvp[14]};
        float[] row3 = {mvp[3], mvp[7], mvp[11], mvp[15]};
        // Left = row3 + row0, Right = row3 - row0, and so on (Gribb-Hartmann extraction).
        add(f.left, row3, row0);
        sub(f.right, row3, row0);
        add(f.bottom, row3, row1);
        sub(f.top, row3, row1);
        add(f.near, row3, row2);
        sub(f.far, row3, row2);
        return f;
    }

    /**
     * Whether an axis-aligned box intersects (or is inside) the frustum. A box fully behind
     * the camera, or entirely off to one side, returns false.
     */
    public boolean intersectsBox(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
        return !outside(left, minX, minY, minZ, maxX, maxY, maxZ)
                && !outside(right, minX, minY, minZ, maxX, maxY, maxZ)
                && !outside(bottom, minX, minY, minZ, maxX, maxY, maxZ)
                && !outside(top, minX, minY, minZ, maxX, maxY, maxZ)
                && !outside(near, minX, minY, minZ, maxX, maxY, maxZ)
                && !outside(far, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Whether a single point is inside the frustum (used for text labels). */
    public boolean containsPoint(double x, double y, double z) {
        return planeValue(left, x, y, z) >= 0
                && planeValue(right, x, y, z) >= 0
                && planeValue(bottom, x, y, z) >= 0
                && planeValue(top, x, y, z) >= 0
                && planeValue(near, x, y, z) >= 0
                && planeValue(far, x, y, z) >= 0;
    }

    /**
     * True when the box lies entirely on the negative side of {@code plane}. Uses the box's
     * most-positive vertex along the plane normal, so a box touching or crossing the plane is
     * kept rather than culled.
     */
    private static boolean outside(float[] plane, double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        float a = plane[0];
        float b = plane[1];
        float c = plane[2];
        float d = plane[3];
        // The positive vertex: the corner furthest along the plane normal.
        double px = a >= 0 ? maxX : minX;
        double py = b >= 0 ? maxY : minY;
        double pz = c >= 0 ? maxZ : minZ;
        return a * px + b * py + c * pz + d < 0;
    }

    private static double planeValue(float[] plane, double x, double y, double z) {
        return plane[0] * x + plane[1] * y + plane[2] * z + plane[3];
    }

    private static void add(float[] out, float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] + b[i];
        }
    }

    private static void sub(float[] out, float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            out[i] = a[i] - b[i];
        }
    }
}
