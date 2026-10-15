package com.logvex.crystal.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the ESP projection maths. These are pure functions, so they can be pinned exactly:
 * a wrong sign or a transposed matrix would otherwise only show up as a box drawn in the
 * wrong place on screen.
 */
class ScreenProjectorTest {

    private static final float EPS = 1e-3f;

    @Test
    void pointAtCameraCentreProjectsToScreenCentre() {
        // Identity-ish view-projection: a point on the forward axis at w=1.
        float[] mvp = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 1,
                0, 0, 0, 0
        };
        ScreenProjector.Projected p = ScreenProjector.project(mvp, 0, 0, 1, 800, 600);
        assertNotNull(p);
        assertEquals(400f, p.x(), EPS);
        assertEquals(300f, p.y(), EPS);
    }

    @Test
    void ndcTopMapsToScreenTop() {
        // y = +1 in NDC should land at screen y = 0 (top-left origin).
        float[] mvp = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 1,
                0, 0, 0, 0
        };
        ScreenProjector.Projected p = ScreenProjector.project(mvp, 0, 1, 1, 800, 600);
        assertEquals(400f, p.x(), EPS);
        assertEquals(0f, p.y(), EPS);
    }

    @Test
    void behindCameraReturnsNull() {
        float[] mvp = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 1,
                0, 0, 0, 0
        };
        // w = mvp[3]*x + mvp[7]*y + mvp[11]*z + mvp[15] = 0 here -> not in front.
        assertNull(ScreenProjector.project(mvp, 0, 0, 0, 800, 600));
    }

    @Test
    void projectBoxReturnsScreenBounds() {
        float[] mvp = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 1,
                0, 0, 0, 0
        };
        float[] box = ScreenProjector.projectBox(mvp, -1, -1, 1, 1, 1, 1, 800, 600);
        assertNotNull(box);
        // minX at NDC -1 -> 0, maxX at NDC +1 -> 800.
        assertEquals(0f, box[0], EPS);
        assertEquals(0f, box[1], EPS);
        assertEquals(800f, box[2], EPS);
        assertEquals(600f, box[3], EPS);
    }

    @Test
    void projectBoxFullyBehindCameraReturnsNull() {
        float[] mvp = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 1,
                0, 0, 0, 0
        };
        assertNull(ScreenProjector.projectBox(mvp, -1, -1, 0, 1, 1, 0, 800, 600));
    }

    @Test
    void viewProjectionPlacesForwardPointInFrontOfCamera() {
        // Camera at origin facing +Z (yaw 0, pitch 0). A point 5 blocks ahead must project
        // to the screen centre and have positive depth.
        float[] mvp = ViewProjection.build(0, 0, 0, 0f, 0f, 70f, 800, 600, 0.05f, 512f);
        ScreenProjector.Projected p = ScreenProjector.project(mvp, 0, 0, 5, 800, 600);
        assertNotNull(p);
        assertEquals(400f, p.x(), 0.5f);
        assertEquals(300f, p.y(), 0.5f);
        assertTrue(p.depth() > 0);
    }

    @Test
    void viewProjectionPointBehindCameraIsRejected() {
        float[] mvp = ViewProjection.build(0, 0, 0, 0f, 0f, 70f, 800, 600, 0.05f, 512f);
        assertNull(ScreenProjector.project(mvp, 0, 0, -5, 800, 600));
    }

    @Test
    void viewProjectionMovesWithCameraTranslation() {
        // A camera at (10,0,0) looking +Z: the world point (10,0,5) is 5 ahead, on centre.
        float[] mvp = ViewProjection.build(10, 0, 0, 0f, 0f, 70f, 800, 600, 0.05f, 512f);
        ScreenProjector.Projected p = ScreenProjector.project(mvp, 10, 0, 5, 800, 600);
        assertNotNull(p);
        assertEquals(400f, p.x(), 0.5f);
        assertEquals(300f, p.y(), 0.5f);
    }

    @Test
    void viewProjectionOffsetPointIsToTheLeftForEastFacingSouth() {
        // Camera at origin facing +Z (south). In Minecraft, +X (east) is on the player's
        // left when facing south, so the point must project into the left half of the screen.
        float[] mvp = ViewProjection.build(0, 0, 0, 0f, 0f, 70f, 800, 600, 0.05f, 512f);
        ScreenProjector.Projected east = ScreenProjector.project(mvp, 3, 0, 5, 800, 600);
        ScreenProjector.Projected west = ScreenProjector.project(mvp, -3, 0, 5, 800, 600);
        assertNotNull(east);
        assertNotNull(west);
        assertTrue(east.x() < 400f, "east should be on the left when facing south");
        assertTrue(west.x() > 400f, "west should be on the right when facing south");
    }
}
