package com.logvex.crystal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the time-based animation helper used across the GUI. */
class AnimatorTest {

    @Test
    void convergesTowardTarget() {
        Animator animator = new Animator(0f, 0.25f);
        animator.setTarget(1f);
        animator.update(16.6667f, 1f);
        float first = animator.value();
        assertTrue(first > 0f && first < 1f);
        for (int i = 0; i < 100; i++) {
            animator.update(16.6667f, 1f);
        }
        assertEquals(1f, animator.value(), 1e-3);
        assertTrue(animator.isSettled());
    }

    @Test
    void zeroSpeedSnapsInstantly() {
        Animator animator = new Animator(0f, 0.25f);
        animator.setTarget(1f);
        animator.update(16f, 0f);
        assertEquals(1f, animator.value(), 1e-6);
    }

    @Test
    void speedScalesProgress() {
        Animator slow = new Animator(0f, 0.25f);
        Animator fast = new Animator(0f, 0.25f);
        slow.setTarget(1f);
        fast.setTarget(1f);
        slow.update(16.6667f, 0.5f);
        fast.update(16.6667f, 2f);
        assertTrue(fast.value() > slow.value());
    }

    @Test
    void snapJumpsWithoutAnimation() {
        Animator animator = new Animator(0f, 0.25f);
        animator.setTarget(1f);
        animator.snap(0.4f);
        assertEquals(0.4f, animator.value(), 1e-6);
        assertEquals(0.4f, animator.target(), 1e-6);
    }

    @Test
    void smoothIsMonotonicOnUnitInterval() {
        Animator animator = new Animator(0f);
        assertEquals(0f, animator.smooth(), 1e-6);
        animator.snap(0.5f);
        assertEquals(0.5f, animator.smooth(), 1e-6);
        animator.snap(1f);
        assertEquals(1f, animator.smooth(), 1e-6);
    }

    @Test
    void startsSettledAtInitialValue() {
        Animator animator = new Animator(0.7f);
        assertTrue(animator.isSettled());
        assertFalse(Math.abs(animator.value() - 0.7f) > 1e-6);
    }
}
