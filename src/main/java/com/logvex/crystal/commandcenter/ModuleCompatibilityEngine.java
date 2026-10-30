package com.logvex.crystal.commandcenter;

import com.logvex.crystal.coordination.ConflictReport;
import com.logvex.crystal.coordination.ModuleCoordinationService;
import com.logvex.crystal.coordination.ModuleDependency;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers "can I use this module, and what does it need" for every registered module.
 *
 * <p>It is a thin, read-only layer over the existing {@link ModuleCoordinationService} and
 * {@link ModuleManager}: the blockers and warnings are the very same findings the
 * {@code ; conflicts} command reports, regrouped per module, and the requirements are the
 * module's own declared metadata. Nothing here enables, disables or reconfigures a module,
 * so asking for a verdict can never change the user's setup.
 *
 * <p>Minecraft-independent: both collaborators are testable with stubs, so the whole engine
 * has direct unit coverage.
 */
public final class ModuleCompatibilityEngine {

    private final ModuleManager modules;
    private final ModuleCoordinationService coordination;

    public ModuleCompatibilityEngine(ModuleManager modules, ModuleCoordinationService coordination) {
        this.modules = modules;
        this.coordination = coordination;
    }

    /** The verdict for one module, or a "unknown module" verdict when it is not registered. */
    public CompatibilityVerdict evaluate(Module module) {
        if (module == null) {
            return new CompatibilityVerdict("unknown", HealthStatus.ATTENTION, false,
                    List.of("module is not registered"), List.of(), List.of());
        }
        ConflictReport report = coordination == null ? new ConflictReport() : coordination.inspect();
        return verdict(module, report);
    }

    /** The verdict for every registered module, in registration order. */
    public List<CompatibilityVerdict> evaluateAll() {
        if (modules == null) {
            return List.of();
        }
        ConflictReport report = coordination == null ? new ConflictReport() : coordination.inspect();
        List<CompatibilityVerdict> result = new ArrayList<>();
        for (Module module : modules.all()) {
            result.add(verdict(module, report));
        }
        return result;
    }

    /** Every module keyed by its lower-case name. */
    public Map<String, CompatibilityVerdict> byName() {
        Map<String, CompatibilityVerdict> result = new LinkedHashMap<>();
        for (CompatibilityVerdict verdict : evaluateAll()) {
            result.put(verdict.module().toLowerCase(java.util.Locale.ROOT), verdict);
        }
        return result;
    }

    /** Only the modules that cannot currently operate. */
    public List<CompatibilityVerdict> blocked() {
        List<CompatibilityVerdict> result = new ArrayList<>();
        for (CompatibilityVerdict verdict : evaluateAll()) {
            if (!verdict.operable()) {
                result.add(verdict);
            }
        }
        return result;
    }

    /** A one-line roll-up, e.g. {@code "2 of 69 modules blocked, 3 with warnings"}. */
    public String summary() {
        List<CompatibilityVerdict> all = evaluateAll();
        int blocked = 0;
        int warned = 0;
        for (CompatibilityVerdict verdict : all) {
            if (!verdict.operable()) {
                blocked++;
            } else if (!verdict.warnings().isEmpty()) {
                warned++;
            }
        }
        if (blocked == 0 && warned == 0) {
            return "all " + all.size() + " modules compatible";
        }
        return blocked + " of " + all.size() + " modules blocked, " + warned + " with warnings";
    }

    private CompatibilityVerdict verdict(Module module, ConflictReport report) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (ConflictReport.Issue issue : report.issues()) {
            if (!module.name().equalsIgnoreCase(issue.module())) {
                continue;
            }
            if (issue.severity() == ConflictReport.Severity.BLOCKING) {
                blockers.add(issue.message());
            } else if (issue.severity() == ConflictReport.Severity.WARNING) {
                warnings.add(issue.message());
            }
        }

        List<String> requirements = new ArrayList<>();
        for (ModuleDependency dependency : module.typedDependencies()) {
            requirements.add(dependency.kind().label() + ":" + dependency.name()
                    + (dependency.required() ? "" : " (optional)"));
        }
        for (String legacy : module.dependencies()) {
            requirements.add("module:" + legacy);
        }

        HealthStatus status = !blockers.isEmpty() ? HealthStatus.CRITICAL
                : !warnings.isEmpty() ? HealthStatus.ATTENTION : HealthStatus.OK;
        return new CompatibilityVerdict(module.name(), status, blockers.isEmpty(), blockers,
                warnings, requirements);
    }
}
