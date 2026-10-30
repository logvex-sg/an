package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.MiniAIManager;
import com.logvex.crystal.ai.crash.CrashMonitor;
import com.logvex.crystal.ai.report.ReportService;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the optional mini-AI subsystem: enablement, model lifecycle, the inference queue
 * and the crash/report backlogs.
 *
 * <p>It reads only the shared AI services, so it is Minecraft-independent and unit testable.
 * Nothing here loads a model or sends anything; it reports the state the services already
 * hold, exactly as {@code ; ai status} does.
 */
public final class AIStatusProvider implements StatusProvider {

    private final AISettings settings;
    private final MiniAIManager ai;
    private final CrashMonitor crashes;
    private final ReportService reports;

    public AIStatusProvider(AISettings settings, MiniAIManager ai, CrashMonitor crashes,
                            ReportService reports) {
        this.settings = settings;
        this.ai = ai;
        this.crashes = crashes;
        this.reports = reports;
    }

    @Override
    public String id() {
        return "ai";
    }

    @Override
    public String title() {
        return "AI Assistant";
    }

    @Override
    public StatusSection snapshot() {
        if (settings == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("AI", "unavailable")));
        }
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(settings.enabled
                ? StatusEntry.ok("Enabled", "on")
                : StatusEntry.info("Enabled", "off"));
        entries.add(StatusEntry.ok("Local analysis", settings.localAnalysis ? "on" : "off"));
        entries.add(StatusEntry.ok("Email", settings.emailEnabled ? "on" : "off"));

        if (ai != null) {
            boolean busy = ai.queueDepth() > 0 || ai.state() == MiniAIManager.State.LOADING;
            entries.add(new StatusEntry("Model", ai.currentSpec().displayName() + " \u00B7 " + ai.state().label(),
                    busy ? com.logvex.crystal.commandcenter.HealthStatus.INFO
                            : com.logvex.crystal.commandcenter.HealthStatus.OK, ""));
            entries.add(new StatusEntry("Queue", ai.queueDepth() + "/" + ai.queueLimit()
                    + " \u00B7 " + ai.completedCount() + " done \u00B7 " + ai.rejectedCount() + " dropped",
                    ai.rejectedCount() > 0 ? com.logvex.crystal.commandcenter.HealthStatus.ATTENTION
                            : com.logvex.crystal.commandcenter.HealthStatus.OK, ""));
            if (!ai.lastError().isBlank()) {
                entries.add(StatusEntry.attention("Last error", ai.lastError(), ""));
            }
        }

        if (crashes != null) {
            entries.add(new StatusEntry("Crashes", crashes.size() + " captured",
                    crashes.size() > 0 ? com.logvex.crystal.commandcenter.HealthStatus.ATTENTION
                            : com.logvex.crystal.commandcenter.HealthStatus.OK, ""));
        }
        if (reports != null) {
            entries.add(StatusEntry.ok("Reports", reports.reports().size() + " queued"));
        }
        String summary = (settings.enabled ? "enabled" : "disabled")
                + (ai != null ? ", model " + ai.state().label().toLowerCase(java.util.Locale.ROOT) : "");
        return new StatusSection(id(), title(), summary, entries);
    }
}
