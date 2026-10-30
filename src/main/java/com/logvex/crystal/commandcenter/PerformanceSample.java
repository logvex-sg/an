package com.logvex.crystal.commandcenter;

/**
 * A point-in-time view of the client's runtime cost, assembled from the frame clock, the
 * module tick timings and the subsystem backlogs the client already tracks.
 *
 * <p>It carries no Minecraft types: the monitor fills it from primitives, so the
 * intelligence that judges it is unit testable and the HUD, the command surface and the AI
 * context all read the same numbers. Every field has a sentinel meaning when unknown rather
 * than a fabricated value.
 *
 * @param fps                 frames per second over the sampling window, or -1 when unknown
 * @param avgFrameMillis      mean frame time over the window, or -1 when unknown
 * @param p95FrameMillis      the 95th-percentile frame time (the stutter the eye notices)
 * @param jitterMillis        frame-time standard deviation, a proxy for stutter
 * @param worstModuleName     the module with the largest tick cost, or empty
 * @param worstModuleTickMillis worst single module tick cost in the window
 * @param totalModuleTickMillis combined module tick cost in the last sampled tick
 * @param activeModules       how many modules are currently enabled
 * @param aiQueueDepth        queued local-inference requests
 * @param aiRejected          inference requests dropped because the queue was full
 * @param eventBacklog        events currently retained in the shared event bus
 * @param activeActions       client actions currently in flight in the coordinator
 * @param sampledAtMillis     when this sample was taken
 */
public record PerformanceSample(
        int fps,
        double avgFrameMillis,
        double p95FrameMillis,
        double jitterMillis,
        String worstModuleName,
        double worstModuleTickMillis,
        double totalModuleTickMillis,
        int activeModules,
        int aiQueueDepth,
        int aiRejected,
        int eventBacklog,
        int activeActions,
        long sampledAtMillis) {

    /** Sentinel for an unmeasured numeric field. */
    public static final int UNKNOWN_INT = -1;
    public static final double UNKNOWN_DOUBLE = -1d;

    /** An empty sample, used before the first frame has been measured. */
    public static PerformanceSample empty(long nowMillis) {
        return new PerformanceSample(UNKNOWN_INT, UNKNOWN_DOUBLE, UNKNOWN_DOUBLE, UNKNOWN_DOUBLE,
                "", UNKNOWN_DOUBLE, UNKNOWN_DOUBLE, 0, 0, 0, 0, 0, nowMillis);
    }

    public boolean hasFrameData() {
        return fps >= 0 && avgFrameMillis > 0;
    }
}
