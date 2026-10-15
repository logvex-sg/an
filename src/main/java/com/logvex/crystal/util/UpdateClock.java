package com.logvex.crystal.util;

/**
 * A tiny millisecond rate limiter so an expensive per-tick task runs at most once every
 * {@code intervalMs} rather than every tick.
 *
 * <p>Modules that scan the world or rebuild a cached list use this instead of running the
 * work on every client tick, which keeps their cost bounded and configurable. Not
 * thread-safe: it is meant to be owned by one module and used from the client thread.
 */
public final class UpdateClock {

    private int intervalMs;
    private long lastRunMs;

    public UpdateClock(long intervalMs) {
        this.intervalMs = (int) Math.max(0, intervalMs);
    }

    /** Changes the interval; affects the next {@link #ready()} evaluation. */
    public void setIntervalMs(long intervalMs) {
        this.intervalMs = (int) Math.max(0, intervalMs);
    }

    /** True when at least {@code intervalMs} has elapsed since the last accepted call. */
    public boolean ready() {
        long now = System.currentTimeMillis();
        if (now - lastRunMs >= intervalMs) {
            lastRunMs = now;
            return true;
        }
        return false;
    }

    /** Forces the next {@link #ready()} call to succeed. */
    public void reset() {
        lastRunMs = 0;
    }
}
