package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reports the module system: how many are registered and enabled, the keybind situation, and
 * the accumulated module failures.
 *
 * <p>It reads only the {@link ModuleManager} and the shared {@link ModuleErrorLog}, both of
 * which are Minecraft-independent, so this provider is directly unit testable. The failure
 * count is the same one {@code ; errors} reports, so the Command Center and the command never
 * disagree.
 */
public final class ModuleStatusProvider implements StatusProvider {

    private final ModuleManager modules;
    private final ModuleErrorLog errors;

    public ModuleStatusProvider(ModuleManager modules, ModuleErrorLog errors) {
        this.modules = modules;
        this.errors = errors;
    }

    @Override
    public String id() {
        return "modules";
    }

    @Override
    public String title() {
        return "Modules";
    }

    @Override
    public StatusSection snapshot() {
        if (modules == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Modules", "unavailable")));
        }
        List<StatusEntry> entries = new ArrayList<>();
        int total = modules.size();
        int enabled = modules.enabledCount();
        entries.add(StatusEntry.ok("Loaded", total + " modules"));
        entries.add(StatusEntry.ok("Enabled", enabled + " of " + total));

        int unbound = 0;
        for (Module module : modules.all()) {
            if (!module.keybind().isBound()) {
                unbound++;
            }
        }
        entries.add(StatusEntry.ok("Keybinds", (total - unbound) + " bound"));

        if (modules.hasKeybindConflicts()) {
            entries.add(StatusEntry.attention("Keybinds", "conflicts detected",
                    "run ; conflicts to see the clashing modules"));
        }

        if (errors != null && errors.distinctModules() > 0) {
            StringBuilder worst = new StringBuilder();
            int shown = 0;
            for (Map.Entry<String, Integer> entry : errors.ranked()) {
                if (shown++ >= 3) {
                    break;
                }
                if (worst.length() > 0) {
                    worst.append(", ");
                }
                worst.append(entry.getKey()).append(" \u00D7").append(entry.getValue());
            }
            entries.add(StatusEntry.attention("Errors",
                    errors.totalRecorded() + " across " + errors.distinctModules() + " module(s)",
                    worst.toString()));
        } else {
            entries.add(StatusEntry.ok("Errors", "none"));
        }

        String summary = enabled + "/" + total + " enabled"
                + (errors != null && errors.totalRecorded() > 0 ? ", " + errors.totalRecorded() + " error(s)" : "");
        return new StatusSection(id(), title(), summary, entries);
    }

    /** Convenience for the HUD: the rolled-up health of this section. */
    public HealthStatus health() {
        return snapshot().worst();
    }
}
