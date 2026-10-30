package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The single entry point to the Command Center: it aggregates every registered
 * {@link StatusProvider} into one {@link ClientStatusModel}, owns the module compatibility
 * engine, the performance monitor/intelligence and the central action registry.
 *
 * <p>It is deliberately a thin orchestrator, not a re-implementation. Each subsystem already
 * knows its own state; the service only asks each provider for a section and rolls the
 * answers up. A provider that throws is isolated and reported as an unhealthy section, so
 * one broken subsystem cannot blank the whole control centre.
 *
 * <p>Minecraft-independent: the providers, the compatibility engine and the performance
 * monitor are all injected, so the whole service is unit testable with stubs.
 */
public final class CommandCenterService {

    private final List<StatusProvider> providers = new ArrayList<>();
    private final ModuleCompatibilityEngine compatibility;
    private final PerformanceMonitor performanceMonitor;
    private final PerformanceIntelligence performanceIntelligence;
    private final CommandRegistry actions = new CommandRegistry();
    private PerformanceReport lastPerformance = null;
    private ClientStatusModel cachedStatus = null;

    public CommandCenterService(ModuleCompatibilityEngine compatibility,
                                PerformanceMonitor performanceMonitor,
                                PerformanceIntelligence performanceIntelligence) {
        this.compatibility = compatibility;
        this.performanceMonitor = performanceMonitor == null ? new PerformanceMonitor() : performanceMonitor;
        this.performanceIntelligence = performanceIntelligence == null
                ? new PerformanceIntelligence() : performanceIntelligence;
    }

    // ------------------------------------------------------------------ providers

    /** Registers a status provider. Duplicate ids are rejected. */
    public void addProvider(StatusProvider provider) {
        if (provider == null) {
            return;
        }
        for (StatusProvider existing : providers) {
            if (existing.id().equalsIgnoreCase(provider.id())) {
                throw new IllegalStateException("Duplicate status provider id: " + provider.id());
            }
        }
        providers.add(provider);
    }

    public List<StatusProvider> providers() {
        return List.copyOf(providers);
    }

    // ------------------------------------------------------------------ status

    /** Builds the current snapshot of the whole client and caches it. */
    public ClientStatusModel snapshot() {
        long now = System.currentTimeMillis();
        List<StatusSection> sections = new ArrayList<>();
        for (StatusProvider provider : providers) {
            sections.add(safeSnapshot(provider));
        }
        sections.add(performanceSection());
        cachedStatus = new ClientStatusModel(sections, now);
        return cachedStatus;
    }

    /**
     * The most recent snapshot, or a freshly built one when none exists. The HUD uses this so
     * rendering never triggers a scan; {@link #snapshot()} is the explicit refresh.
     */
    public ClientStatusModel statusModel() {
        if (cachedStatus == null) {
            return snapshot();
        }
        return cachedStatus;
    }

    /** The status section for one provider id, or null when none is registered. */
    public StatusSection section(String id) {
        for (StatusProvider provider : providers) {
            if (provider.id().equalsIgnoreCase(id)) {
                return safeSnapshot(provider);
            }
        }
        if ("performance".equalsIgnoreCase(id)) {
            return performanceSection();
        }
        return null;
    }

    private StatusSection safeSnapshot(StatusProvider provider) {
        try {
            StatusSection section = provider.snapshot();
            if (section != null) {
                return section;
            }
        } catch (Throwable t) {
            return new StatusSection(provider.id(), provider.title(), "",
                    List.of(StatusEntry.critical(provider.title(), "provider failed",
                            String.valueOf(t.getMessage()))));
        }
        return new StatusSection(provider.id(), provider.title(), "", List.of());
    }

    private StatusSection performanceSection() {
        PerformanceReport report = performance();
        List<StatusEntry> entries = new ArrayList<>();
        PerformanceSample sample = report.sample();
        if (sample.hasFrameData()) {
            entries.add(new StatusEntry("FPS", String.valueOf(sample.fps()),
                    sample.fps() < PerformanceIntelligence.CRITICAL_FPS ? HealthStatus.CRITICAL
                            : sample.fps() < PerformanceIntelligence.LOW_FPS ? HealthStatus.ATTENTION
                            : HealthStatus.OK, ""));
            entries.add(StatusEntry.ok("Frame", String.format("%.1fms avg, %.1fms p95",
                    sample.avgFrameMillis(), sample.p95FrameMillis())));
        } else {
            entries.add(StatusEntry.info("FPS", "no frame data yet"));
        }
        entries.add(StatusEntry.ok("Modules", sample.activeModules() + " enabled"));
        if (!sample.worstModuleName().isBlank()) {
            entries.add(new StatusEntry("Worst tick",
                    sample.worstModuleName() + " " + String.format("%.1fms", sample.worstModuleTickMillis()),
                    sample.worstModuleTickMillis() > PerformanceIntelligence.MODULE_TICK_MILLIS
                            ? HealthStatus.ATTENTION : HealthStatus.OK, ""));
        }
        entries.add(StatusEntry.ok("Actions", sample.activeActions() + " in flight"));
        for (PerformanceFinding finding : report.findings()) {
            entries.add(new StatusEntry("Finding", finding.observation(), finding.severity(),
                    finding.suggestion()));
        }
        return new StatusSection("performance", "Performance", report.summary(), entries);
    }

    // ------------------------------------------------------------------ performance

    /** Records one rendered frame's duration for the rolling statistics. */
    public void recordFrame(double deltaMillis) {
        performanceMonitor.onFrame(deltaMillis);
    }

    public PerformanceMonitor performanceMonitor() {
        return performanceMonitor;
    }

    /**
     * Builds a {@link PerformanceSample} from the measured frames and the given subsystem
     * counters, judges it, caches the report and returns it. The counters are supplied by the
     * caller so the service never reaches into another subsystem.
     */
    public PerformanceReport samplePerformance(long nowMillis, int activeModules, String worstModuleName,
                                               double worstModuleTickMillis, double totalModuleTickMillis,
                                               int aiQueueDepth, int aiRejected, int eventBacklog,
                                               int activeActions) {
        PerformanceSample sample = performanceMonitor.sample(nowMillis, activeModules, worstModuleName,
                worstModuleTickMillis, totalModuleTickMillis, aiQueueDepth, aiRejected,
                eventBacklog, activeActions);
        lastPerformance = performanceIntelligence.analyse(sample);
        return lastPerformance;
    }

    /** The most recent performance report, computing an empty one if none has been sampled. */
    public PerformanceReport performance() {
        if (lastPerformance == null) {
            lastPerformance = performanceIntelligence.analyse(
                    PerformanceSample.empty(System.currentTimeMillis()));
        }
        return lastPerformance;
    }

    // ------------------------------------------------------------------ compatibility

    public ModuleCompatibilityEngine compatibility() {
        return compatibility;
    }

    /** The verdict for a module by name, or null when the engine is unavailable. */
    public CompatibilityVerdict verdictFor(String moduleName) {
        if (compatibility == null || moduleName == null) {
            return null;
        }
        for (CompatibilityVerdict verdict : compatibility.evaluateAll()) {
            if (verdict.module().equalsIgnoreCase(moduleName)) {
                return verdict;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ actions

    public CommandRegistry actions() {
        return actions;
    }

    /** Runs a registered action by id and returns its result. */
    public ActionResult run(String actionId) {
        return actions.run(actionId);
    }

    // ------------------------------------------------------------------ tick

    /**
     * Refreshes the cached status snapshot. The caller throttles how often this runs, so the
     * expensive part (asking every provider) happens at a low rate rather than every frame.
     */
    public void refreshStatus() {
        snapshot();
    }
}
