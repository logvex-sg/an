package com.logvex.crystal.scan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decides whether a freshly observed cluster is worth reporting, suppressing repeats.
 *
 * <p>Two independent guards, both required:
 * <ul>
 *   <li><b>Proximity.</b> A cluster whose centre is within {@code proximityRadius} of an
 *       already-reported cluster in the same dimension is the same find, even if its centre
 *       moved slightly as edge chunks loaded. This is the primary check.</li>
 *   <li><b>Cooldown.</b> The same cluster is not reported again until
 *       {@code cooldownMillis} have passed. This bounds the report rate if a base is
 *       re-scanned.</li>
 * </ul>
 *
 * <p>Deliberately Minecraft-free and clock-injected ({@code nowMillis} is passed in), so
 * every suppression rule is unit tested deterministically.
 */
public final class ClusterReporter {

    /** A previously reported cluster, remembered for suppression. */
    public record Seen(int x, int y, int z, long lastReportedMillis, int chestCount) {
    }

    private final List<Seen> seen = new ArrayList<>();
    private final int proximityRadius;
    private final long cooldownMillis;

    public ClusterReporter(int proximityRadius, long cooldownMillis) {
        this.proximityRadius = Math.max(0, proximityRadius);
        this.cooldownMillis = Math.max(0L, cooldownMillis);
    }

    /**
     * Returns true and records the cluster when it should be reported, or false when it is
     * suppressed as a duplicate (too close to a recent report, still within cooldown).
     */
    public boolean shouldReport(ChestCluster cluster, long nowMillis) {
        long radiusSquared = (long) proximityRadius * proximityRadius;
        Seen nearby = null;
        for (Seen candidate : seen) {
            long dx = candidate.x() - cluster.centerX();
            long dy = candidate.y() - cluster.centerY();
            long dz = candidate.z() - cluster.centerZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                nearby = candidate;
                break;
            }
        }
        if (nearby != null && nowMillis - nearby.lastReportedMillis() < cooldownMillis) {
            return false;
        }
        if (nearby != null) {
            // Cooldown elapsed: refresh the record in place rather than accumulating.
            seen.set(seen.indexOf(nearby),
                    new Seen(cluster.centerX(), cluster.centerY(), cluster.centerZ(), nowMillis, cluster.chestCount()));
            return true;
        }
        seen.add(new Seen(cluster.centerX(), cluster.centerY(), cluster.centerZ(), nowMillis, cluster.chestCount()));
        return true;
    }

    /** Forgets every remembered cluster, e.g. after a dimension change. */
    public void clear() {
        seen.clear();
    }

    public int size() {
        return seen.size();
    }

    /** Drops records whose cooldown has fully elapsed and that are no longer relevant. */
    public void prune(long nowMillis, long maxAgeMillis) {
        Iterator<Seen> iterator = seen.iterator();
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().lastReportedMillis() > maxAgeMillis) {
                iterator.remove();
            }
        }
    }
}
