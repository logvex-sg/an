package com.logvex.crystal.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the frustum culling used by the ESP render pass against the same
 * {@link ViewProjection} matrix the renderer builds, so the culling decision and the
 * on-screen projection can never disagree.
 */
class FrustumTest {

    // A camera at the origin looking down +Z (yaw 0 faces +Z in Minecraft's convention).
    private static final float[] MVP = ViewProjection.build(
            0, 0, 0, 0f, 0f, 70f, 1920, 1080, 0.05f, 512f);

    @Test
    void boxAheadOfTheCameraIsVisible() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        // Straight ahead, within range.
        assertTrue(frustum.intersectsBox(-0.5, -0.5, 10, 0.5, 0.5, 11));
    }

    @Test
    void boxBehindTheCameraIsCulled() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        // The camera looks toward +Z, so a box at negative Z is behind it.
        assertFalse(frustum.intersectsBox(-0.5, -0.5, -11, 0.5, 0.5, -10));
    }

    @Test
    void boxFarToTheSideIsCulled() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        // Far outside the horizontal field of view.
        assertFalse(frustum.intersectsBox(5000, -0.5, 10, 5001, 0.5, 11));
    }

    @Test
    void boxBeyondTheFarPlaneIsCulled() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        assertFalse(frustum.intersectsBox(-0.5, -0.5, 600, 0.5, 0.5, 601));
    }

    @Test
    void boxStraddlingTheCameraIsKept() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        // A box the camera is inside must never be culled, even though it spans the near plane.
        assertTrue(frustum.intersectsBox(-1, -1, -1, 1, 1, 1));
    }

    @Test
    void pointVisibilityMatchesBoxVisibility() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        assertTrue(frustum.containsPoint(0, 0, 20));
        assertFalse(frustum.containsPoint(0, 0, -20));
        assertFalse(frustum.containsPoint(9000, 0, 20));
    }

    @Test
    void aPointJustInsideTheEdgeIsVisibleAndJustOutsideIsNot() {
        Frustum frustum = Frustum.fromMatrix(MVP);
        // At z=10, the half-height is tan(35 deg) * 10 ~= 7.0 blocks.
        assertTrue(frustum.containsPoint(0, 6.5, 10));
        assertFalse(frustum.containsPoint(0, 40, 10));
    }

    @Test
    void rotatedCameraCullsWhatItHasTurnedAwayFrom() {
        // Looking down -Z (yaw 180) reverses what is in front.
        float[] turned = ViewProjection.build(0, 0, 0, 180f, 0f, 70f, 1920, 1080, 0.05f, 512f);
        Frustum frustum = Frustum.fromMatrix(turned);
        assertTrue(frustum.intersectsBox(-0.5, -0.5, -11, 0.5, 0.5, -10));
        assertFalse(frustum.intersectsBox(-0.5, -0.5, 10, 0.5, 0.5, 11));
    }

    @Test
    void rejectsAnIncompleteMatrix() {
        assertThrows(IllegalArgumentException.class, () -> Frustum.fromMatrix(new float[8]));
    }
}
