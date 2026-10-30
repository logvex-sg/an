package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The verdict of the performance intelligence for one {@link PerformanceSample}: the sample
 * itself, the findings the rules produced, and a rolled-up health.
 *
 * <p>It is a pure value so the whole judgement is reproducible and testable. The findings
 * are ordered most-severe-first so a compact UI shows the important one without sorting.
 */
public final class PerformanceReport {

    private final PerformanceSample sample;
    private final List<PerformanceFinding> findings;
    private final HealthStatus status;

    public PerformanceReport(PerformanceSample sample, List<PerformanceFinding> findings) {
        this.sample = sample;
        List<PerformanceFinding> copy = findings == null ? List.of() : new ArrayList<>(findings);
        copy.sort((a, b) -> Integer.compare(b.severity().ordinal(), a.severity().ordinal()));
        this.findings = List.copyOf(copy);
        HealthStatus worst = HealthStatus.OK;
        for (PerformanceFinding finding : copy) {
            worst = worst.worseOf(finding.severity());
        }
        this.status = worst;
    }

    public PerformanceSample sample() {
        return sample;
    }

    public List<PerformanceFinding> findings() {
        return findings;
    }

    public HealthStatus status() {
        return status;
    }

    public boolean clean() {
        return findings.isEmpty();
    }

    /** A one-line roll-up for the HUD and the command surface. */
    public String summary() {
        if (!sample.hasFrameData()) {
            return "performance: no frame data yet";
        }
        String head = "fps " + sample.fps()
                + " \u00B7 frame " + String.format("%.1f", sample.avgFrameMillis()) + "ms"
                + " \u00B7 jitter " + String.format("%.1f", sample.jitterMillis()) + "ms";
        return findings.isEmpty() ? head + " \u00B7 healthy" : head + " \u00B7 " + findings.size() + " finding(s)";
    }
}
