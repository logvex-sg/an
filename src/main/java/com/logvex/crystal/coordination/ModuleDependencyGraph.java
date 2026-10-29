package com.logvex.crystal.coordination;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The typed dependency graph over the registered modules.
 *
 * <p>It stores each module's {@link ModuleDependency} edges, evaluates them against a
 * {@link DependencyContext}, detects circular module chains, and produces a per-module
 * explanation of what is missing and why. It never mutates module state: it only reports.
 *
 * <p>Module names are matched case-insensitively (keys are normalised to lower case) while the
 * original spelling is kept for display, so reports read "AutoCrystal" rather than
 * "autocrystal". Minecraft-independent and unit testable. The live client fills it from
 * {@code ModuleManager} once at bootstrap and re-evaluates on demand.
 */
public final class ModuleDependencyGraph {

    /** The state of one dependency edge for a module. */
    public record EdgeStatus(ModuleDependency dependency, boolean satisfied) {
    }

    /**
     * The evaluation of one module: whether it can operate, the unsatisfied required edges,
     * the unsatisfied optional edges, and a short explanation.
     */
    public record ModuleReport(String module, boolean operable, List<EdgeStatus> missingRequired,
                               List<EdgeStatus> missingOptional, String explanation) {

        public boolean hasRequiredProblem() {
            return !missingRequired.isEmpty();
        }
    }

    private final Map<String, List<ModuleDependency>> edges = new LinkedHashMap<>();
    private final Map<String, Set<String>> conflicts = new LinkedHashMap<>();
    private final Map<String, String> displayNames = new LinkedHashMap<>();

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void remember(String module) {
        displayNames.putIfAbsent(key(module), module);
    }

    /** Registers (or replaces) the dependency edges declared by {@code module}. */
    public void declare(String module, List<ModuleDependency> dependencies) {
        if (module == null || module.isBlank()) {
            return;
        }
        remember(module);
        edges.put(key(module), dependencies == null ? List.of() : List.copyOf(dependencies));
    }

    /** Registers the mutually-conflicting module names declared by {@code module}. */
    public void declareConflicts(String module, List<String> conflicting) {
        if (module == null || module.isBlank()) {
            return;
        }
        remember(module);
        Set<String> set = new LinkedHashSet<>();
        if (conflicting != null) {
            for (String name : conflicting) {
                if (name != null && !name.isBlank()) {
                    remember(name);
                    set.add(key(name));
                }
            }
        }
        conflicts.put(key(module), set);
    }

    /** Every module that has declared edges or conflicts, in its original spelling. */
    public Set<String> knownModules() {
        Set<String> all = new LinkedHashSet<>();
        for (String k : edges.keySet()) {
            all.add(displayNames.getOrDefault(k, k));
        }
        for (String k : conflicts.keySet()) {
            all.add(displayNames.getOrDefault(k, k));
        }
        return all;
    }

    public List<ModuleDependency> dependenciesOf(String module) {
        return edges.getOrDefault(key(module), List.of());
    }

    /** Evaluates one module against {@code context}. */
    public ModuleReport evaluate(String module, DependencyContext context) {
        List<EdgeStatus> missingRequired = new ArrayList<>();
        List<EdgeStatus> missingOptional = new ArrayList<>();
        for (ModuleDependency dependency : dependenciesOf(module)) {
            boolean ok = dependency.satisfied(context);
            if (ok) {
                continue;
            }
            EdgeStatus status = new EdgeStatus(dependency, false);
            if (dependency.required()) {
                missingRequired.add(status);
            } else {
                missingOptional.add(status);
            }
        }
        boolean operable = missingRequired.isEmpty();
        String display = displayNames.getOrDefault(key(module), module);
        return new ModuleReport(display, operable, List.copyOf(missingRequired),
                List.copyOf(missingOptional), explain(display, missingRequired));
    }

    /** Evaluates every known module. */
    public List<ModuleReport> evaluateAll(DependencyContext context) {
        List<ModuleReport> reports = new ArrayList<>();
        for (String module : knownModules()) {
            reports.add(evaluate(module, context));
        }
        return reports;
    }

    private static String explain(String module, List<EdgeStatus> missingRequired) {
        if (missingRequired.isEmpty()) {
            return module + " is ready";
        }
        StringBuilder sb = new StringBuilder(module).append(" cannot operate: ");
        for (int i = 0; i < missingRequired.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(missingRequired.get(i).dependency().reason());
        }
        return sb.toString();
    }

    /**
     * Detects circular chains formed by module edges. Only {@code MODULE} edges participate:
     * service/integration/capability edges point outside the module graph and cannot cycle.
     *
     * @return each cycle as a list of module names (the first repeated at the end), or an empty
     *         list when the graph is acyclic
     */
    public List<List<String>> findCycles() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        for (String module : knownModules()) {
            if (!visited.contains(key(module))) {
                dfs(key(module), visited, onStack, path, cycles);
            }
        }
        return cycles;
    }

    private void dfs(String module, Set<String> visited, Set<String> onStack,
                     Deque<String> path, List<List<String>> cycles) {
        visited.add(module);
        onStack.add(module);
        path.addLast(module);
        for (ModuleDependency dependency : dependenciesOf(module)) {
            if (!dependency.isModuleEdge()) {
                continue;
            }
            String next = key(dependency.name());
            if (onStack.contains(next)) {
                cycles.add(cycleFrom(path, next));
            } else if (!visited.contains(next) && edges.containsKey(next)) {
                dfs(next, visited, onStack, path, cycles);
            }
        }
        path.removeLast();
        onStack.remove(module);
    }

    /** Extracts the cycle portion of the current DFS path, ending with the repeated node. */
    private static List<String> cycleFrom(Deque<String> path, String repeated) {
        List<String> cycle = new ArrayList<>();
        boolean started = false;
        for (String node : path) {
            if (node.equals(repeated)) {
                started = true;
            }
            if (started) {
                cycle.add(node);
            }
        }
        cycle.add(repeated);
        return cycle;
    }

    /**
     * A module whose keybind shares a key with another module: both would toggle from one
     * press. Returns module name -&gt; list of other module names sharing its key.
     */
    public Map<String, List<String>> findKeybindConflicts(Map<String, Integer> boundKeys) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (boundKeys == null) {
            return result;
        }
        Map<Integer, List<String>> byKey = new HashMap<>();
        for (Map.Entry<String, Integer> entry : boundKeys.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            byKey.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        for (List<String> sharers : byKey.values()) {
            if (sharers.size() > 1) {
                for (String module : sharers) {
                    List<String> others = new ArrayList<>(sharers);
                    others.remove(module);
                    result.put(module, others);
                }
            }
        }
        return result;
    }

    /**
     * Modules that declare a conflict against each other but are both currently enabled.
     *
     * @return one entry per active conflicting pair, as {@code [a, b]} in registration order
     */
    public List<List<String>> findActiveConflicts(Set<String> enabledModules) {
        List<List<String>> active = new ArrayList<>();
        if (enabledModules == null) {
            return active;
        }
        Set<String> enabledLower = new HashSet<>();
        for (String name : enabledModules) {
            enabledLower.add(key(name));
        }
        Set<String> seenPairs = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : conflicts.entrySet()) {
            String module = entry.getKey();
            if (!enabledLower.contains(module)) {
                continue;
            }
            for (String other : entry.getValue()) {
                if (!enabledLower.contains(other)) {
                    continue;
                }
                // Deduplicate the unordered pair, whichever direction declared it.
                String pairKey = module.compareTo(other) <= 0 ? module + "|" + other : other + "|" + module;
                if (seenPairs.add(pairKey)) {
                    active.add(List.of(displayNames.getOrDefault(module, module),
                            displayNames.getOrDefault(other, other)));
                }
            }
        }
        return active;
    }

    public int size() {
        return edges.size();
    }
}
