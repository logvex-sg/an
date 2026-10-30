package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the optional Baritone integration: whether it is detected, its version, and the
 * current navigation state.
 *
 * <p>Read-only over the live {@link BaritoneService}. When Baritone is absent the section is
 * a calm {@code Info}, not a fault: the integration is optional by design, and the Command
 * Center must not make an optional feature look broken.
 */
public final class BaritoneStatusProvider implements StatusProvider {

    private final BaritoneService baritone;

    public BaritoneStatusProvider(BaritoneService baritone) {
        this.baritone = baritone;
    }

    @Override
    public String id() {
        return "baritone";
    }

    @Override
    public String title() {
        return "Baritone";
    }

    @Override
    public StatusSection snapshot() {
        if (baritone == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Baritone", "unavailable")));
        }
        BaritoneStatus status = baritone.status();
        List<StatusEntry> entries = new ArrayList<>();
        if (!status.available()) {
            entries.add(StatusEntry.info("Integration", "not detected"));
            if (!status.reason().isBlank()) {
                entries.add(StatusEntry.info("Reason", status.reason()));
            }
            return new StatusSection(id(), title(), "optional, not installed", entries);
        }
        entries.add(StatusEntry.ok("Version", status.version()));
        entries.add(StatusEntry.ok("Goal", status.hasGoal()
                ? status.goalType() + " " + status.goalDetail() : "none"));
        entries.add(StatusEntry.ok("Path", status.calculating() ? "calculating"
                : status.hasPath() ? status.pathLength() + " nodes" : "none"));
        if (status.paused()) {
            entries.add(StatusEntry.info("Navigation", "paused"));
        }
        String error = baritone.lastActionError();
        if (error != null && !error.isBlank()) {
            entries.add(StatusEntry.attention("Last action", error, ""));
        }
        return new StatusSection(id(), title(), status.statusText(), entries);
    }
}
