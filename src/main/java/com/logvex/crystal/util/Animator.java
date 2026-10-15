package com.logvex.crystal.util;

/**
 * Smoothly animates a single float toward a target. Used for GUI open/close transitions,
 * hover fades, toggle slides and notification fades.
 *
 * <p>Animation is time-based (not frame-based) so it looks identical at any frame rate.
 * A speed multiplier of 0 freezes the value at its target, which is how the reduced-motion
 * setting disables animation without branching at every call site.
 */
public final class Animator {

    private float value;
    private float target;
    private final float baseSpeed;

    public Animator(float initial) {
        this(initial, 0.18f);
    }

    /**
     * @param initial    starting value
     * @param baseSpeed  fraction of the remaining distance closed per 16 ms at speed 1
     */
    public Animator(float initial, float baseSpeed) {
        this.value = initial;
        this.target = initial;
        this.baseSpeed = baseSpeed;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    /** Jumps straight to a value with no animation. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
    }

    public boolean isSettled() {
        return Math.abs(target - value) < 0.001f;
    }

    /**
     * Advances the animation.
     *
     * @param deltaMillis elapsed time since the last update
     * @param speed       animation speed multiplier (0 = instant)
     * @return the new value
     */
    public float update(float deltaMillis, float speed) {
        if (speed <= 0f || baseSpeed <= 0f) {
            value = target;
            return value;
        }
        float step = baseSpeed * (deltaMillis / 16.6667f) * speed;
        step = Math.max(0f, Math.min(1f, step));
        value += (target - value) * step;
        if (Math.abs(target - value) < 0.001f) {
            value = target;
        }
        return value;
    }

    /** Advances toward {@code target} with the default speed multiplier of 1. */
    public float update(float deltaMillis) {
        return update(deltaMillis, 1f);
    }

    /** Eases the current value with a smoothstep for extra polish in decorative effects. */
    public float smooth() {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}
