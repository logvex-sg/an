package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link PerformanceSample} into {@link PerformanceFinding} advice using a small
 * set of deterministic rules.
 *
 * <p>The rules describe the failure modes an anarchy client actually hits during combat: a
 * frame rate that has dropped below playable, stutter the eye notices, one module whose tick
 * dominates the budget, a saturated local-inference queue, an event backlog, and action
 * contention. Each rule is independent and threshold-based, so the judgement is reproducible
 * and unit testable, and the whole thing works with no AI model present.
 */
public final class PerformanceIntelligence {

    /** Frames below this are called out as degraded during combat. */
    static final int LOW_FPS = 60;
    /** Frames below this are called out as critical. */
    static final int CRITICAL_FPS = 30;
    /** A single frame slower than this is a visible hitch. */
    static final double HITCH_MILLIS = 50d;
    /** Jitter above this reads as stutter even when the average frame rate is fine. */
    static final double JITTER_MILLIS = 12d;
    /** One module spending more than this per tick is a suspect. */
    static final double MODULE_TICK_MILLIS = 3d;
    /** The local-inference queue at or above this is saturated. */
    static final int AI_QUEUE_SATURATED = 2;

    /** Judges {@code sample} and returns the report. */
    public PerformanceReport analyse(PerformanceSample sample) {
        if (sample == null) {
            sample = PerformanceSample.empty(System.currentTimeMillis());
        }
        List<PerformanceFinding> findings = new ArrayList<>();

        if (sample.hasFrameData()) {
            if (sample.fps() < CRITICAL_FPS) {
                findings.add(new PerformanceFinding("low-fps-critical", HealthStatus.CRITICAL,
                        "frame rate has dropped to " + sample.fps() + " fps",
                        "close the ClickGUI or disable heavy render modules before fighting",
                        sample.fps() + " fps"));
            } else if (sample.fps() < LOW_FPS) {
                findings.add(new PerformanceFinding("low-fps", HealthStatus.ATTENTION,
                        "frame rate is " + sample.fps() + " fps",
                        "check which render modules are enabled",
                        sample.fps() + " fps"));
            }
            if (sample.p95FrameMillis() > HITCH_MILLIS) {
                findings.add(new PerformanceFinding("frame-hitch", HealthStatus.ATTENTION,
                        String.format("frames occasionally take %.0fms", sample.p95FrameMillis()),
                        "a hitch this long is usually a world scan or a resource reload",
                        String.format("%.0fms p95", sample.p95FrameMillis())));
            }
            if (sample.jitterMillis() > JITTER_MILLIS) {
                findings.add(new PerformanceFinding("frame-jitter", HealthStatus.INFO,
                        String.format("frame time varies by %.1fms", sample.jitterMillis()),
                        "consistent frame pacing feels smoother than a high average with spikes",
                        String.format("%.1fms jitter", sample.jitterMillis())));
            }
        }

        if (sample.worstModuleTickMillis() > MODULE_TICK_MILLIS && !sample.worstModuleName().isBlank()) {
            findings.add(new PerformanceFinding("module-tick", HealthStatus.ATTENTION,
                    String.format("module '%s' spends %.1fms per tick",
                            sample.worstModuleName(), sample.worstModuleTickMillis()),
                    "review its settings; a module over 3ms per tick is a common cause of lag",
                    String.format("%.1fms", sample.worstModuleTickMillis())));
        }

        if (sample.aiQueueDepth() >= AI_QUEUE_SATURATED) {
            findings.add(new PerformanceFinding("ai-queue", HealthStatus.ATTENTION,
                    "local inference queue is saturated (" + sample.aiQueueDepth() + " waiting)",
                    "reduce inference threads or idle-unload the model sooner",
                    sample.aiQueueDepth() + " queued"));
        }
        if (sample.aiRejected() > 0) {
            findings.add(new PerformanceFinding("ai-dropped", HealthStatus.INFO,
                    sample.aiRejected() + " inference request(s) were dropped",
                    "requests are dropped rather than queued when the model is busy",
                    sample.aiRejected() + " dropped"));
        }

        if (sample.eventBacklog() > 0) {
            findings.add(new PerformanceFinding("event-backlog", HealthStatus.INFO,
                    sample.eventBacklog() + " event(s) retained in the bus",
                    "a growing backlog means a subscriber is not draining events",
                    sample.eventBacklog() + " events"));
        }
        if (sample.activeActions() > 1) {
            findings.add(new PerformanceFinding("action-contention", HealthStatus.ATTENTION,
                    sample.activeActions() + " client actions are in flight at once",
                    "two modules acting on the same input can fight; check ; conflicts",
                    sample.activeActions() + " active"));
        }

        return new PerformanceReport(sample, findings);
    }
}
