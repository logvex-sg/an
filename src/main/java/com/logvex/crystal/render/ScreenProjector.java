package com.logvex.crystal.render;

/**
 * Pure world-to-screen projection maths, kept free of Minecraft types so it can be unit
 * tested. The ESP layers feed it the combined view-projection matrix (as a JOML
 * column-major {@code float[16]}) and a world position; it returns the screen pixel and
 * the view-space depth, or {@code null} when the point is behind the camera.
 *
 * <p>Having this in one place means every ESP module projects consistently and a
 * projection bug is a test failure rather than a mystery on screen.
 */
public final class ScreenProjector {

    private ScreenProjector() {
    }

    /** A projected point: screen x/y in pixels and the view-space depth (positive = in front). */
    public record Projected(float x, float y, float depth) {
    }

    /**
     * Projects a world point through {@code mvp} into screen space.
     *
     * @param mvp        column-major 4x4 view-projection matrix
     * @param x          world x
     * @param y          world y
     * @param z          world z
     * @param width      viewport width in pixels
     * @param height     viewport height in pixels
     * @return the projected point, or null if it is behind the camera (w &le; 0)
     */
    public static Projected project(float[] mvp, double x, double y, double z, int width, int height) {
        double cx = mvp[0] * x + mvp[4] * y + mvp[8] * z + mvp[12];
        double cy = mvp[1] * x + mvp[5] * y + mvp[9] * z + mvp[13];
        double cz = mvp[2] * x + mvp[6] * y + mvp[10] * z + mvp[14];
        double cw = mvp[3] * x + mvp[7] * y + mvp[11] * z + mvp[15];
        if (cw <= 1.0e-5) {
            return null;
        }
        float ndcX = (float) (cx / cw);
        float ndcY = (float) (cy / cw);
        float screenX = (ndcX * 0.5f + 0.5f) * width;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * height;
        return new Projected(screenX, screenY, (float) cw);
    }

    /**
     * Projects the eight corners of an axis-aligned box and returns the 2-D bounding
     * rectangle {@code {minX, minY, maxX, maxY}} in screen pixels, or {@code null} when the
     * whole box is behind the camera. The result is not clipped to the viewport; callers
     * that need that can test the rectangle themselves.
     */
    public static float[] projectBox(float[] mvp, double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ, int width, int height) {
        float sx0 = Float.MAX_VALUE;
        float sy0 = Float.MAX_VALUE;
        float sx1 = -Float.MAX_VALUE;
        float sy1 = -Float.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? minX : maxX;
            double py = (i & 2) == 0 ? minY : maxY;
            double pz = (i & 4) == 0 ? minZ : maxZ;
            Projected p = project(mvp, px, py, pz, width, height);
            if (p == null) {
                continue;
            }
            any = true;
            sx0 = Math.min(sx0, p.x());
            sy0 = Math.min(sy0, p.y());
            sx1 = Math.max(sx1, p.x());
            sy1 = Math.max(sy1, p.y());
        }
        return any ? new float[]{sx0, sy0, sx1, sy1} : null;
    }
}
