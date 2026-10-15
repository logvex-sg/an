package com.logvex.crystal.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the toast slide-and-fade motion curve. */
class ToastMotionTest {

    @Test
    void fullySettledInTheMiddleOfLife() {
        assertEquals(1f, ToastMotion.reveal(0.5f), 1e-6f);
    }

    @Test
    void startsHiddenAndEndsHidden() {
        assertEquals(0f, ToastMotion.reveal(0f), 1e-6f);
        assertEquals(0f, ToastMotion.reveal(1f), 1e-6f);
    }

    @Test
    void easesInMonotonically() {
        float previous = -1f;
        for (int i = 0; i <= 10; i++) {
            float r = ToastMotion.reveal(i / 10f * ToastMotion.IN_FRACTION);
            assertTrue(r >= previous, "reveal must not decrease while easing in");
            previous = r;
        }
        assertTrue(previous > 0.9f, "reveal must approach 1 by the end of the in-window");
    }

    @Test
    void slideIsZeroWhenSettledAndFullWhenHidden() {
        assertEquals(0f, ToastMotion.slide(20, 1f), 1e-6f);
        assertEquals(20f, ToastMotion.slide(20, 0f), 1e-6f);
    }

    @Test
    void ageHelperHandlesZeroLifetime() {
        assertEquals(1f, ToastMotion.reveal(5L, 0L), 1e-6f);
    }
}
