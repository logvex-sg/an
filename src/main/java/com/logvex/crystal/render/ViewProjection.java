package com.logvex.crystal.render;

/**
 * Builds a column-major view-projection matrix from a camera pose, so the ESP layers can
 * project world points to the screen without touching Minecraft's render matrices.
 *
 * <p>Working from the camera's own yaw/pitch/position and the vertical FOV keeps the whole
 * projection in one testable place and avoids depending on GL matrix-stack state, which is
 * fragile across render events. The convention matches Minecraft: yaw 0 faces +Z, a
 * positive pitch looks down, and {@code +Y} is up. The projection is a standard
 * right-handed perspective looking down view-space {@code -Z}.
 *
 * <p>Matrix layout is column-major with element {@code (row, col)} at index
 * {@code col * 4 + row}, which is what {@link ScreenProjector} expects and what OpenGL
 * consumes.
 */
public final class ViewProjection {

    private ViewProjection() {
    }

    /**
     * Builds a view-projection matrix.
     *
     * @param camX    camera world x
     * @param camY    camera world y
     * @param camZ    camera world z
     * @param yawDeg  camera yaw in degrees (0 faces +Z)
     * @param pitchDeg camera pitch in degrees (positive looks down)
     * @param fovDeg  vertical field of view in degrees
     * @param width   viewport width
     * @param height  viewport height
     * @param near    near plane
     * @param far     far plane
     * @return a fresh column-major 4x4 matrix
     */
    public static float[] build(double camX, double camY, double camZ, float yawDeg, float pitchDeg,
                                float fovDeg, int width, int height, float near, float far) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        // Minecraft's rotation vector for (yaw, pitch).
        double fx = -Math.sin(yaw) * cosPitch;
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * cosPitch;
        // Right = normalize(forward x worldUp); up = right x forward.
        double rx = -fz;
        double ry = 0;
        double rz = fx;
        double rLen = Math.hypot(rx, rz);
        if (rLen < 1.0e-6) {
            rx = 1;
            rz = 0;
            rLen = 1;
        }
        rx /= rLen;
        rz /= rLen;
        // up = right x forward
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        float[] view = new float[16];
        // Row 0 = right
        view[0] = (float) rx;
        view[4] = (float) ry;
        view[8] = (float) rz;
        view[12] = (float) -(rx * camX + ry * camY + rz * camZ);
        // Row 1 = up
        view[1] = (float) ux;
        view[5] = (float) uy;
        view[9] = (float) uz;
        view[13] = (float) -(ux * camX + uy * camY + uz * camZ);
        // Row 2 = -forward
        view[2] = (float) -fx;
        view[6] = (float) -fy;
        view[10] = (float) -fz;
        view[14] = (float) (fx * camX + fy * camY + fz * camZ);
        // Row 3
        view[3] = 0;
        view[7] = 0;
        view[11] = 0;
        view[15] = 1;

        float aspect = height <= 0 ? 1f : (float) width / height;
        float[] proj = perspective(fovDeg, aspect, near, far);
        return multiply(proj, view);
    }

    /** A standard right-handed perspective matrix (column-major). */
    public static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) / 2.0));
        float[] m = new float[16];
        m[0] = f / (aspect <= 0 ? 1f : aspect);
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    /** Multiplies two column-major matrices: {@code result = a * b}. */
    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                r[col * 4 + row] = sum;
            }
        }
        return r;
    }
}
