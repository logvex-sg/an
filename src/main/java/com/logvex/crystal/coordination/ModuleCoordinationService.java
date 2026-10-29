package com.logvex.crystal.coordination;

import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single inspector over the client's module coordination: typed dependencies, circular
 * chains, active conflicts, keybind clashes and action contention.
 *
 * <p>It reads the live {@link ModuleManager} and a {@link DependencyContext}, builds a
 * {@link ModuleDependencyGraph}, and produces a {@link ConflictReport}. It is deliberately
 * read-only: it never enables, disables or reconfigures a module, so asking "what is wrong"
 * can never change the user's setup.
 *
 * <p>Minecraft-independent: the module manager and dependency context are both testable with
 * stubs, so the whole inspector is unit tested without a running game.
 */
public final class ModuleCoordinationService {

    private final ModuleManager modules;
    private final DependencyContext context;
    private final ActionCoordinator actions;
    private final ModuleDependencyGraph graph = new ModuleDependencyGraph();

    public ModuleCoordinationService(ModuleManager modules, DependencyContext context,
                                     ActionCoordinator actions) {
        this.modules = modules;
        this.context = context == null ? DependencyContext.empty() : context;
        this.actions = actions == null ? new ActionCoordinator() : actions;
        rebuild();
    }

    /** Rebuilds the dependency graph from the current module declarations. */
    public void rebuild() {
        if (modules == null) {
            return;
        }
        for (Module module : modules.all()) {
            List<ModuleDependency> dependencies = new ArrayList<>(module.typedDependencies());
            for (String legacy : module.dependencies()) {
                dependencies.add(ModuleDependency.module(legacy));
            }
            graph.declare(module.name(), dependencies);
            graph.declareConflicts(module.name(), module.conflicts());
        }
    }

    public ModuleDependencyGraph graph() {
        return graph;
    }

    public ActionCoordinator actions() {
        return actions;
    }

    /**
     * Explains why {@code module} cannot currently operate, or null when it can. Used by the
     * GUI and command surface to answer "why is this disabled".
     */
    public String blockerFor(Module module) {
        if (module == null) {
            return "unknown module";
        }
        ModuleDependencyGraph.ModuleReport report = graph.evaluate(module.name(), context);
        if (report.operable()) {
            return null;
        }
        return report.explanation();
    }

    /** True when {@code module}'s typed + legacy dependencies are all satisfied. */
    public boolean canOperate(Module module) {
        return module != null && graph.evaluate(module.name(), context).operable();
    }

    /** Inspects the client and returns every coordination issue found. */
    public ConflictReport inspect() {
        ConflictReport report = new ConflictReport();

        for (ModuleDependencyGraph.ModuleReport moduleReport : graph.evaluateAll(context)) {
            for (ModuleDependencyGraph.EdgeStatus edge : moduleReport.missingRequired()) {
                ModuleDependency dependency = edge.dependency();
                ConflictReport.Category category = dependency.kind() == ModuleDependency.Kind.INTEGRATION
                        ? ConflictReport.Category.MISSING_INTEGRATION
                        : ConflictReport.Category.MISSING_DEPENDENCY;
                report.add(ConflictReport.Severity.BLOCKING, category, moduleReport.module(),
                        dependency.reason() + " (unavailable)");
            }
        }

        for (List<String> cycle : graph.findCycles()) {
            String chain = String.join(" \u2192 ", cycle);
            for (String node : cycle) {
                report.add(ConflictReport.Severity.BLOCKING, ConflictReport.Category.CIRCULAR_DEPENDENCY,
                        node, "circular dependency: " + chain);
            }
        }

        if (modules != null) {
            Set<String> enabled = new HashSet<>();
            Map<String, Integer> boundKeys = new HashMap<>();
            for (Module module : modules.all()) {
                if (module.isEnabled()) {
                    enabled.add(module.name().toLowerCase(java.util.Locale.ROOT));
                }
                if (module.keybind().isBound()) {
                    boundKeys.put(module.name(), module.keybind().key());
                }
            }
            for (List<String> pair : graph.findActiveConflicts(enabled)) {
                report.add(ConflictReport.Severity.WARNING, ConflictReport.Category.ACTIVE_MODULE_CONFLICT,
                        pair.get(0), "conflicts with enabled module " + pair.get(1));
                report.add(ConflictReport.Severity.WARNING, ConflictReport.Category.ACTIVE_MODULE_CONFLICT,
                        pair.get(1), "conflicts with enabled module " + pair.get(0));
            }
            for (Map.Entry<String, List<String>> entry : graph.findKeybindConflicts(boundKeys).entrySet()) {
                report.add(ConflictReport.Severity.WARNING, ConflictReport.Category.KEYBIND_CONFLICT,
                        entry.getKey(), "keybind shared with " + String.join(", ", entry.getValue()));
            }
        }

        Map<String, String> occupied = actions.occupiedGroups();
        for (Map.Entry<String, String> entry : occupied.entrySet()) {
            report.add(ConflictReport.Severity.INFO, ConflictReport.Category.ACTION_CONTENTION,
                    entry.getValue(), "holding the '" + entry.getKey() + "' action group");
        }
        return report;
    }

    /** Module names whose required dependencies are all satisfied, in registration order. */
    public List<String> operableModules() {
        List<String> result = new ArrayList<>();
        if (modules == null) {
            return result;
        }
        for (Module module : modules.all()) {
            if (canOperate(module)) {
                result.add(module.name());
            }
        }
        return result;
    }

    /** Distinct module names that participate in any declared conflict, for the GUI badge. */
    public Set<String> conflictedModules() {
        Set<String> names = new LinkedHashSet<>();
        for (ConflictReport.Issue issue : inspect().issues()) {
            if (issue.category() == ConflictReport.Category.ACTIVE_MODULE_CONFLICT
                    || issue.category() == ConflictReport.Category.CIRCULAR_DEPENDENCY) {
                names.add(issue.module());
            }
        }
        return names;
    }
}
