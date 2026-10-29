package com.logvex.crystal.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers typed dependency evaluation, cycle detection and conflict discovery. */
class ModuleDependencyGraphTest {

    /** A dependency context with the named facts turned on. */
    private static DependencyContext context(Set<String> modules, Set<String> services,
                                             Set<String> integrations, Set<String> capabilities) {
        return new DependencyContext() {
            @Override
            public boolean moduleInstalled(String name) {
                return modules.contains(name.toLowerCase());
            }

            @Override
            public boolean moduleEnabled(String name) {
                return modules.contains(name.toLowerCase());
            }

            @Override
            public boolean serviceAvailable(String name) {
                return services.contains(name.toLowerCase());
            }

            @Override
            public boolean integrationAvailable(String name) {
                return integrations.contains(name.toLowerCase());
            }

            @Override
            public boolean capabilityAvailable(String name) {
                return capabilities.contains(name.toLowerCase());
            }

            @Override
            public boolean modeActive(String mode) {
                return false;
            }
        };
    }

    @Test
    void moduleEdgeIsSatisfiedOnlyWhenTheModuleIsEnabled() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("AutoCrystal", List.of(ModuleDependency.module("Combat")));

        assertTrue(graph.evaluate("AutoCrystal", context(Set.of("combat"), Set.of(), Set.of(), Set.of()))
                .operable());
        assertFalse(graph.evaluate("AutoCrystal", context(Set.of(), Set.of(), Set.of(), Set.of()))
                .operable());
    }

    @Test
    void missingRequiredDependencyIsReportedWithAReason() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("AutoCrystal", List.of(ModuleDependency.module("Combat")));

        ModuleDependencyGraph.ModuleReport report =
                graph.evaluate("AutoCrystal", DependencyContext.empty());
        assertFalse(report.operable());
        assertEquals(1, report.missingRequired().size());
        assertTrue(report.explanation().contains("Combat"));
    }

    @Test
    void optionalDependencyDoesNotBlockOperation() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("AutoCrystal", List.of(ModuleDependency.optionalIntegration("baritone")));

        ModuleDependencyGraph.ModuleReport report =
                graph.evaluate("AutoCrystal", DependencyContext.empty());
        assertTrue(report.operable());
        assertEquals(1, report.missingOptional().size());
    }

    @Test
    void integrationDependencyIsEvaluatedAgainstTheIntegrationProbe() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("BaritoneRoute", List.of(ModuleDependency.integration("baritone")));

        assertFalse(graph.evaluate("BaritoneRoute", DependencyContext.empty()).operable());
        assertTrue(graph.evaluate("BaritoneRoute",
                context(Set.of(), Set.of(), Set.of("baritone"), Set.of())).operable());
    }

    @Test
    void circularModuleDependencyIsDetected() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("A", List.of(ModuleDependency.module("B")));
        graph.declare("B", List.of(ModuleDependency.module("C")));
        graph.declare("C", List.of(ModuleDependency.module("A")));

        List<List<String>> cycles = graph.findCycles();
        assertFalse(cycles.isEmpty(), "a 3-node cycle must be found");
        List<String> cycle = cycles.get(0);
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1), "cycle ends where it starts");
        assertTrue(cycle.contains("a") && cycle.contains("b") && cycle.contains("c"));
    }

    @Test
    void acyclicGraphHasNoCycles() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("A", List.of(ModuleDependency.module("B")));
        graph.declare("B", List.of(ModuleDependency.service("combat")));
        assertTrue(graph.findCycles().isEmpty());
    }

    @Test
    void nonModuleEdgesCannotFormACycle() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declare("A", List.of(ModuleDependency.service("b")));
        graph.declare("B", List.of(ModuleDependency.service("a")));
        assertTrue(graph.findCycles().isEmpty(), "service edges are outside the module graph");
    }

    @Test
    void keybindConflictsAreGrouped() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        Map<String, Integer> keys = Map.of("Sprint", 82, "AutoWalk", 82, "Tracers", 84);
        Map<String, List<String>> conflicts = graph.findKeybindConflicts(keys);
        assertEquals(2, conflicts.size());
        assertTrue(conflicts.get("Sprint").contains("AutoWalk"));
        assertFalse(conflicts.containsKey("Tracers"));
    }

    @Test
    void activeConflictsReportedOncePerPair() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.declareConflicts("AutoWalk", List.of("FastStop"));
        graph.declareConflicts("FastStop", List.of("AutoWalk"));

        List<List<String>> active = graph.findActiveConflicts(Set.of("autowalk", "faststop"));
        assertEquals(1, active.size(), "a mutual conflict is one pair, not two");
        assertTrue(active.get(0).contains("AutoWalk") && active.get(0).contains("FastStop"),
                "the original spelling is preserved for display");
        assertTrue(graph.findActiveConflicts(Set.of("autowalk")).isEmpty());
    }
}
