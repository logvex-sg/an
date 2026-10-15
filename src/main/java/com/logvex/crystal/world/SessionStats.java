package com.logvex.crystal.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Tracks per-session statistics: elapsed play time, distance walked, and the furthest the
 * player has travelled from where the session started.
 *
 * <p>The counter advances only while a world is loaded and the player is alive, so time
 * spent on menus or disconnected is not counted. Sampling happens once per client tick
 * (20 Hz); the accumulated distance uses the horizontal move distance between ticks, which
 * is stable and cheap. It is a plain value holder with no Minecraft state retained beyond
 * the last position, so it is trivially safe to reset on disconnect.
 */
public final class SessionStats {

    private static SessionStats instance;

    private long startedMillis;
    private long activeMillis;
    private long lastSampleMillis;
    private double distanceWalked;
    private Vec3d origin;
    private Vec3d lastPos;
    private double maxDistanceFromOrigin;

    private SessionStats() {
    }

    public static synchronized SessionStats get() {
        if (instance == null) {
            instance = new SessionStats();
        }
        return instance;
    }

    /** Resets every counter and starts a new session. */
    public void reset() {
        startedMillis = System.currentTimeMillis();
        lastSampleMillis = startedMillis;
        activeMillis = 0;
        distanceWalked = 0;
        origin = null;
        lastPos = null;
        maxDistanceFromOrigin = 0;
    }

    /**
     * Advances the session from the current world state. Called once per client tick; a
     * no-op while no world is loaded, and it lazily starts the session on first use.
     */
    public void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (startedMillis == 0) {
            reset();
        }
        if (client == null || client.world == null || client.player == null) {
            // Pause: remember the time but do not accumulate it.
            lastSampleMillis = now;
            lastPos = null;
            return;
        }
        long delta = now - lastSampleMillis;
        // Ignore a first sample and any implausibly long gap (e.g. after a stall).
        if (delta > 0 && delta < 2000) {
            activeMillis += delta;
        }
        lastSampleMillis = now;

        Vec3d pos = client.player.getEntityPos();
        if (origin == null) {
            origin = pos;
        }
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            distanceWalked += Math.sqrt(dx * dx + dz * dz);
        }
        lastPos = pos;
        maxDistanceFromOrigin = Math.max(maxDistanceFromOrigin, origin.distanceTo(pos));
    }

    /** Milliseconds of active play time this session. */
    public long activeMillis() {
        return activeMillis;
    }

    /** Blocks walked horizontally this session. */
    public double distanceWalked() {
        return distanceWalked;
    }

    /** The furthest horizontal distance from the session origin, in blocks. */
    public double maxDistanceFromOrigin() {
        return maxDistanceFromOrigin;
    }

    public Vec3d origin() {
        return origin;
    }
}
