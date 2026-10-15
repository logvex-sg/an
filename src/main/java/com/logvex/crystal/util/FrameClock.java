package com.logvex.crystal.util;

import net.minecraft.client.MinecraftClient;

/**
 * Tracks real frame time so animation is time-based rather than frame-based.
 *
 * <p>Minecraft's render callbacks give a tick delta, but GUI animations need wall-clock
 * milliseconds. This keeps the last frame timestamp and exposes the delta, clamped so a
 * stalled frame (loading a chunk, alt-tab) cannot cause a huge animation jump.
 */
public final class FrameClock {

    private static long lastNanos;
    private static float lastDeltaMillis = 16.6667f;

    private FrameClock() {
    }

    /** Call once per rendered frame. */
    public static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            float delta = (now - lastNanos) / 1_000_000f;
            lastDeltaMillis = Math.max(0f, Math.min(100f, delta));
        }
        lastNanos = now;
    }

    /** Milliseconds elapsed since the previous frame, clamped to [0, 100]. */
    public static float deltaMillis() {
        return lastDeltaMillis;
    }

    /** Resets the clock (e.g. when a screen opens) so the first frame is not a jump. */
    public static void reset() {
        lastNanos = 0;
        lastDeltaMillis = 16.6667f;
    }
}
