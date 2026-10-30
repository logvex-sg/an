package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.coordination.ActionCoordinator;
import com.logvex.crystal.coordination.ConflictReport;
import com.logvex.crystal.coordination.ModuleCoordinationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reports the module-coordination inspector's findings: unsatisfied dependencies, circular
 * chains, active conflicts, keybind clashes and action contention.
 *
 * <p>It is a thin adapter over the existing {@link ModuleCoordinationService} and
 * {@link ActionCoordinator}; the same facts {@code ; conflicts} prints, regrouped into a
 * section. It never resolves a conflict - the inspector is read-only and so is this.
 */
public final class CoordinationStatusProvider implements StatusProvider {

    /** At most this many individual issues are listed, so a broken graph cannot flood the view. */
    private static final int MAX_LISTED_ISSUES = 6;

    private final ModuleCoordinationService coordination;
    private final ActionCoordinator actions;

    public CoordinationStatusProvider(ModuleCoordinationService coordination, ActionCoordinator actions) {
        this.coordination = coordination;
        this.actions = actions;
    }

    @Override
    public String id() {
        return "coordination";
    }

    @Override
    public String title() {
        return "Coordination";
    }

    @Override
    public StatusSection snapshot() {
        if (coordination == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Coordination", "unavailable")));
        }
        ConflictReport report = coordination.inspect();
        List<StatusEntry> entries = new ArrayList<>();

        if (report.clean()) {
            entries.add(StatusEntry.ok("Conflicts", "none"));
        } else {
            long blocking = report.count(ConflictReport.Severity.BLOCKING);
            long warning = report.count(ConflictReport.Severity.WARNING);
            long info = report.count(ConflictReport.Severity.INFO);
            entries.add(new StatusEntry("Blocking", String.valueOf(blocking),
                    blocking > 0 ? HealthStatus.CRITICAL : HealthStatus.OK, ""));
            entries.add(new StatusEntry("Warnings", String.valueOf(warning),
                    warning > 0 ? HealthStatus.ATTENTION : HealthStatus.OK, ""));
            if (info > 0) {
                entries.add(StatusEntry.info("Notices", String.valueOf(info)));
            }
            int shown = 0;
            for (ConflictReport.Issue issue : report.issues()) {
                if (issue.severity() == ConflictReport.Severity.INFO) {
                    continue;
                }
                if (shown++ >= MAX_LISTED_ISSUES) {
                    entries.add(StatusEntry.info("More",
                            Math.max(0, report.issues().size() - MAX_LISTED_ISSUES) + " further issue(s)"));
                    break;
                }
                entries.add(new StatusEntry(issue.category().name(),
                        issue.module() + ": " + issue.message(),
                        issue.severity() == ConflictReport.Severity.BLOCKING
                                ? HealthStatus.CRITICAL : HealthStatus.ATTENTION, ""));
            }
        }

        if (actions != null) {
            Map<String, String> occupied = actions.occupiedGroups();
            entries.add(StatusEntry.ok("Actions", actions.activeCount() + " active"
                    + (occupied.isEmpty() ? "" : ", " + occupied.size() + " group(s) held")));
        }
        return new StatusSection(id(), title(), report.summary(), entries);
    }
}
