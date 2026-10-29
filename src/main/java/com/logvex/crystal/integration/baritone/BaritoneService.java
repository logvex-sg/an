package com.logvex.crystal.integration.baritone;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.notification.NotificationLevel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * The client's single point of contact with Baritone.
 *
 * <p>Owns the {@link BaritoneBridge}, the polling cadence, the pure
 * {@link BaritoneNavigationState} and the navigation notifications. The rest of the client
 * (commands, HUD, GUI, the module) talks to this service only, never to Baritone directly.
 *
 * <p><b>Threading.</b> Baritone's API is only safe to read on the client thread, so polling and
 * every action run there. What runs off-thread is only the {@link #probe() availability probe},
 * which does class loading - the one Baritone operation that can be slow (disk + jar scan) and
 * touches no game state. The probe result is published through {@link AtomicBoolean}s and read
 * by the client-thread poller; a probe is started at most once and never in parallel.
 *
 * <p><b>Failure policy.</b> A failed action is reported to the user and recorded in the history;
 * it never throws into the command or GUI layer. Missing Baritone degrades to a clear
 * "unavailable" diagnostic everywhere.
 */
public final class BaritoneService {

    /** Poll at most every {@value #POLL_INTERVAL_MS} ms, so diagnostics do not run per frame. */
    private static final long POLL_INTERVAL_MS = 500L;
    private static final int HISTORY_CAPACITY = 64;

    private final BaritoneBridge bridge;
    private final BaritoneNavigationState navigation = new BaritoneNavigationState(HISTORY_CAPACITY);
    private final AtomicBoolean probeRunning = new AtomicBoolean();
    private final AtomicBoolean probeDone = new AtomicBoolean();

    private volatile boolean available;
    private volatile String unavailableReason = "not probed yet";
    private volatile String version = "unknown";

    private long lastPollMillis;
    private long lastActionMillis;
    private String lastActionError = "";
    private boolean goalReachedFlag;

    public BaritoneService() {
        this(new ReflectiveBaritoneBridge());
    }

    /** Test/DI constructor. */
    public BaritoneService(BaritoneBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Kicks off the one-time availability probe on a daemon worker. Safe to call every tick: it
     * starts the worker only once and does nothing afterwards.
     */
    public void ensureProbed() {
        if (probeDone.get() || probeRunning.get()) {
            return;
        }
        if (!probeRunning.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::probeBlocking, "logvex-baritone-probe");
        worker.setDaemon(true);
        worker.start();
    }

    /** Runs the availability probe on the calling thread. Shared by the worker and tests. */
    void probeBlocking() {
        try {
            BaritoneBridge.Availability result = bridge.probe();
            available = result.available();
            version = result.version();
            unavailableReason = result.reason();
        } catch (Throwable t) {
            available = false;
            unavailableReason = "Baritone probe failed: " + t;
        } finally {
            probeDone.set(true);
        }
    }

    /** True once the background probe has completed. */
    public boolean probed() {
        return probeDone.get();
    }

    /** Polls Baritone state at the fixed cadence and records transitions. Call on the client thread. */
    public void tick(MinecraftClient client) {
        ensureProbed();
        long now = System.currentTimeMillis();
        if (now - lastPollMillis < POLL_INTERVAL_MS) {
            return;
        }
        lastPollMillis = now;
        poll(now);
    }

    /**
     * Reads Baritone state once and feeds the navigation model. Minecraft-free and
     * time-injectable so it can be driven directly by tests.
     */
    void poll(long now) {
        if (!probeDone.get()) {
            return;
        }
        BaritoneStatus next;
        if (!available) {
            next = BaritoneStatus.unavailable(unavailableReason);
        } else {
            try {
                next = bridge.status();
            } catch (RuntimeException e) {
                next = BaritoneStatus.unavailable("status read failed: " + e.getMessage());
                navigation.history().error("status read failed: " + e.getMessage(), now);
            }
        }
        BaritoneNavigationState.Outcome outcome = navigation.update(next, now);
        if (outcome.goalReached()) {
            goalReachedFlag = true;
        }
    }

    /**
     * Consumes the "an issued goal just completed" flag. Returns true at most once per
     * completion, so the module that owns arrival notifications fires exactly one.
     */
    public boolean consumeGoalReached() {
        boolean value = goalReachedFlag;
        goalReachedFlag = false;
        return value;
    }

    private static void notify(String message, NotificationLevel level) {
        CrystalClient client = CrystalClient.get();
        if (client != null) {
            client.notify(message, level);
        }
    }

    // ------------------------------------------------------------------ queries

    /** The latest status snapshot; never null. */
    public BaritoneStatus status() {
        return navigation.status();
    }

    public boolean available() {
        return available;
    }

    public String version() {
        return version;
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    public BaritoneHistory history() {
        return navigation.history();
    }

    public String lastActionError() {
        return lastActionError;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Sets a goal and starts pathing.
     *
     * @return null on success, or a user-facing error message
     */
    public String goTo(BaritoneGoal goal) {
        if (goal == null) {
            return "No goal given";
        }
        if (!requireAvailable()) {
            return unavailableReason;
        }
        try {
            bridge.gotoGoal(goal.kind(), goal.x(), goal.y(), goal.z());
            navigation.markGoalIssued(goal.describe());
            recordAction("goto " + goal.describe(), null);
            notify("Baritone: heading to " + goal.describe() + " \u2661", NotificationLevel.SUCCESS);
            return null;
        } catch (RuntimeException e) {
            recordAction("goto " + goal.describe(), e.getMessage());
            return e.getMessage();
        }
    }

    /** Follows a block within a radius (Baritone {@code GoalNear}). */
    public String followNear(int x, int y, int z, int range) {
        if (!requireAvailable()) {
            return unavailableReason;
        }
        BaritoneGoal goal;
        try {
            goal = BaritoneGoal.near(x, y, z, range);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        try {
            bridge.follow(goal.kind(), x, y, z, range);
            navigation.markGoalIssued(goal.describe());
            recordAction("near " + x + " " + y + " " + z, null);
            notify("Baritone: approaching within " + range + " \u2661", NotificationLevel.SUCCESS);
            return null;
        } catch (RuntimeException e) {
            recordAction("near", e.getMessage());
            return e.getMessage();
        }
    }

    /** Sends Baritone to the player's current block. */
    public String goToCurrentPosition(MinecraftClient client) {
        if (client == null || client.player == null) {
            return "No player";
        }
        BlockPos pos = client.player.getBlockPos();
        return goTo(BaritoneGoal.block(pos.getX(), pos.getY(), pos.getZ()));
    }

    /** Cancels the current goal. Never throws. */
    public void cancel() {
        if (!requireAvailable()) {
            return;
        }
        try {
            bridge.cancel();
            navigation.markGoalCancelled();
            recordAction("cancel", null);
            notify("Baritone: cancelled", NotificationLevel.INFO);
        } catch (RuntimeException e) {
            recordAction("cancel", e.getMessage());
        }
    }

    /** Pauses pathing when supported. Never throws. */
    public void pause() {
        if (!requireAvailable()) {
            return;
        }
        try {
            bridge.pause();
            recordAction("pause", null);
            notify("Baritone: paused", NotificationLevel.INFO);
        } catch (RuntimeException e) {
            recordAction("pause", e.getMessage());
        }
    }

    /**
     * Writes one Baritone setting through its public API.
     *
     * @return null on success, or a user-facing error message
     */
    public String setSetting(String name, String value) {
        if (!requireAvailable()) {
            return unavailableReason;
        }
        if (name == null || name.isBlank() || value == null) {
            return "Usage: set <setting> <value>";
        }
        try {
            bridge.setSetting(name, value);
            recordAction("set " + name + " " + value, null);
            return null;
        } catch (RuntimeException e) {
            recordAction("set " + name, e.getMessage());
            return e.getMessage();
        }
    }

    /** Reads a Baritone setting, or null when it is not exposed. */
    public String getSetting(String name) {
        if (!available) {
            return null;
        }
        return bridge.getSetting(name);
    }

    /** The names of every writable Baritone setting, sorted; empty when unavailable. */
    public List<String> settingNames() {
        if (!available) {
            return Collections.emptyList();
        }
        try {
            return bridge.settingNames();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /** The client-side command equivalent of {@code action}, for diagnostics. */
    public String commandFor(String action) {
        return bridge.commandFor(action);
    }

    private boolean requireAvailable() {
        if (!probeDone.get()) {
            ensureProbed();
            unavailableReason = "Baritone probe in progress";
            return false;
        }
        return available;
    }

    private void recordAction(String action, String error) {
        lastActionMillis = System.currentTimeMillis();
        if (error != null) {
            lastActionError = error;
            navigation.history().error(action + ": " + error, lastActionMillis);
            notify("Baritone: " + error, NotificationLevel.ERROR);
        } else {
            lastActionError = "";
        }
    }

    /** Milliseconds since the last action was issued, or -1 if none yet. */
    public long millisSinceLastAction() {
        return lastActionMillis == 0 ? -1 : System.currentTimeMillis() - lastActionMillis;
    }
}
