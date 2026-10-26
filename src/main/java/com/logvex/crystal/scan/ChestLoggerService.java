package com.logvex.crystal.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The chest-cluster logger's engine: it turns a snapshot of chest positions into reports.
 *
 * <p>Runs entirely in memory on this machine. It performs no network access and writes no
 * files; a report is only ever handed to the caller's poll method, which surfaces it on the
 * local HUD/chat. Nothing here knows about Discord, HTTP, or any remote service.
 *
 * <p>The scan itself (walking the world) happens on the client thread in the module; the
 * clustering runs on a single daemon worker so a large haul never blocks a frame. The worker
 * is injected, so tests can drive it synchronously.
 */
public final class ChestLoggerService {

    /** A configuration snapshot, applied atomically so a mid-scan change cannot tear. */
    public record Config(int minClusterSize, int linkDistance, int verticalTolerance,
                         int reportThreshold, int proximityRadius, long cooldownMillis) {
    }

    private final ExecutorService worker;
    private final boolean ownsWorker;
    private final AtomicBoolean closed = new AtomicBoolean();

    private final List<ChestReport> pending = Collections.synchronizedList(new ArrayList<>());

    private volatile Config config = new Config(2, 2, 1, 30, 16, 300_000L);
    private volatile ClusterReporter reporter = new ClusterReporter(16, 300_000L);
    private volatile String dimension = "";
    private volatile boolean running;

    /** Production constructor: owns a single daemon worker thread. */
    public ChestLoggerService() {
        this(Executors.newSingleThreadExecutor(daemonFactory()), true);
    }

    /** Test constructor: supply a synchronous or custom executor. */
    public ChestLoggerService(ExecutorService worker, boolean ownsWorker) {
        this.worker = worker;
        this.ownsWorker = ownsWorker;
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "logvex-chest-scan");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        };
    }

    /** Applies a new configuration; resets suppression when the geometry changes. */
    public void configure(Config newConfig) {
        Config previous = this.config;
        this.config = newConfig;
        if (previous.proximityRadius() != newConfig.proximityRadius()
                || previous.cooldownMillis() != newConfig.cooldownMillis()) {
            this.reporter = new ClusterReporter(newConfig.proximityRadius(), newConfig.cooldownMillis());
        }
    }

    public Config config() {
        return config;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    /** Forgets all suppression state, e.g. on disconnect or an explicit reset. */
    public void reset() {
        reporter.clear();
        pending.clear();
    }

    /** Called when the player's dimension changes; clusters in another world are unrelated. */
    public void onDimensionChange(String newDimension) {
        if (newDimension != null && !newDimension.equals(dimension)) {
            dimension = newDimension;
            reporter.clear();
        }
    }

    /**
     * Submits a snapshot of chest positions for background clustering. A no-op unless the
     * logger is running. The snapshot is already a copy, so the caller is free to keep
     * scanning while this runs.
     */
    public void submit(String snapshotDimension, List<ChestPoint> points, List<String> nearbyPlayers,
                       long nowMillis) {
        if (!running || closed.get() || points.isEmpty()) {
            return;
        }
        Config snapshotConfig = config;
        String snapshotPlayersDimension = snapshotDimension == null ? "" : snapshotDimension;
        List<ChestPoint> copy = new ArrayList<>(points);
        List<String> players = new ArrayList<>(nearbyPlayers);
        try {
            worker.execute(() -> cluster(snapshotConfig, snapshotPlayersDimension, copy, players, nowMillis));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Shutting down; drop the frame rather than block the client thread.
        }
    }

    private void cluster(Config snapshotConfig, String snapshotDimension, List<ChestPoint> points,
                         List<String> players, long nowMillis) {
        try {
            List<ChestCluster> clusters = ChestClusterer.cluster(points, snapshotConfig.linkDistance(),
                    snapshotConfig.verticalTolerance(), snapshotConfig.minClusterSize());
            for (ChestCluster cluster : clusters) {
                if (cluster.chestCount() < snapshotConfig.reportThreshold()) {
                    continue;
                }
                if (!reporter.shouldReport(cluster, nowMillis)) {
                    continue;
                }
                String id = cluster.id(snapshotDimension, Math.max(1, snapshotConfig.proximityRadius()));
                pending.add(new ChestReport(id, snapshotDimension, cluster.centerX(), cluster.centerY(),
                        cluster.centerZ(), cluster.chestCount(), nowMillis, players));
            }
        } catch (Throwable ignored) {
            // A malformed snapshot must never take down the worker thread.
        }
    }

    /** Drains and returns the reports produced since the last poll. Called on the client thread. */
    public List<ChestReport> poll() {
        synchronized (pending) {
            if (pending.isEmpty()) {
                return List.of();
            }
            List<ChestReport> drained = new ArrayList<>(pending);
            pending.clear();
            return drained;
        }
    }

    /** Drops stale suppression records. Called periodically from the client thread. */
    public void prune(long nowMillis, long maxAgeMillis) {
        reporter.prune(nowMillis, maxAgeMillis);
    }

    /**
     * How many clusters are currently remembered for duplicate suppression. Bounded by
     * {@link #prune}; exposed so the module and tests can prove it does not grow without limit
     * over a long session in a base-dense world.
     */
    public int suppressionCount() {
        return reporter.size();
    }

    public void close() {
        if (closed.compareAndSet(false, true) && ownsWorker) {
            worker.shutdownNow();
        }
    }
}
