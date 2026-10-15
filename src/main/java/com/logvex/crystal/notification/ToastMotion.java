package com.logvex.crystal.notification;

/**
 * Pure slide-and-fade motion for a toast.
 *
 * <p>A toast animates in over the first fraction of its life and back out over the last
 * fraction, easing on a smoothstep curve. Between those windows it is fully settled. The
 * maths is Minecraft-independent so the animation can be unit tested; the renderer turns the
 * returned values into pixels and alpha.
 */
public final class ToastMotion {

    /** Fraction of the lifetime spent sliding/fading in. */
    public static final float IN_FRACTION = 0.14f;
    /** Fraction of the lifetime spent sliding/fading out. */
    public static final float OUT_FRACTION = 0.28f;

    private ToastMotion() {
    }

    /**
     * The reveal amount at {@code progress} (0 = just posted, 1 = expired).
     *
     * @return 0 while fully hidden, 1 when fully settled, and a smoothstepped value between.
     */
    public static float reveal(float progress) {
        float p = clamp01(progress);
        if (p <= 1f - OUT_FRACTION) {
            float t = p / IN_FRACTION;
            return smoothstep(clamp01(t));
        }
        float t = (1f - p) / OUT_FRACTION;
        return smoothstep(clamp01(t));
    }

    /** Convenience: reveal for an age and lifetime in milliseconds. */
    public static float reveal(long ageMillis, long lifetimeMillis) {
        if (lifetimeMillis <= 0) {
            return 1f;
        }
        return reveal((float) ageMillis / lifetimeMillis);
    }

    /** The horizontal slide distance in pixels for a reveal amount. */
    public static float slide(int maxSlide, float reveal) {
        return maxSlide * (1f - clamp01(reveal));
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
