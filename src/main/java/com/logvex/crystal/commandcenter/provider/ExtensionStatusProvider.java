package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.extension.ExtensionManager;
import com.logvex.crystal.extension.ExtensionState;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the extension subsystem: how many are installed, active and failing.
 *
 * <p>Read-only over the {@link ExtensionManager}. A failed extension is surfaced as an
 * {@code Attention} entry with the reason, so a broken add-on is visible in the Command
 * Center rather than only in a log file.
 */
public final class ExtensionStatusProvider implements StatusProvider {

    private final ExtensionManager extensions;

    public ExtensionStatusProvider(ExtensionManager extensions) {
        this.extensions = extensions;
    }

    @Override
    public String id() {
        return "extensions";
    }

    @Override
    public String title() {
        return "Extensions";
    }

    @Override
    public StatusSection snapshot() {
        if (extensions == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Extensions", "unavailable")));
        }
        List<StatusEntry> entries = new ArrayList<>();
        List<ExtensionState> installed = extensions.installed();
        entries.add(StatusEntry.ok("Installed", String.valueOf(installed.size())));
        entries.add(StatusEntry.ok("Active", String.valueOf(extensions.activeCount())));
        int failures = extensions.failureCount();
        entries.add(new StatusEntry("Failures", String.valueOf(failures),
                failures > 0 ? com.logvex.crystal.commandcenter.HealthStatus.ATTENTION
                        : com.logvex.crystal.commandcenter.HealthStatus.OK, ""));
        if (failures > 0) {
            int shown = 0;
            for (ExtensionState state : installed) {
                if (state.status() == ExtensionState.Status.FAILED && shown++ < 4) {
                    entries.add(StatusEntry.attention(state.id(), state.detail(), ""));
                }
            }
        }
        return new StatusSection(id(), title(), extensions.summary(), entries);
    }
}
