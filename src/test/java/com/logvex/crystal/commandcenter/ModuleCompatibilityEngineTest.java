package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.coordination.ActionCoordinator;
import com.logvex.crystal.coordination.DependencyContext;
import com.logvex.crystal.coordination.ModuleCoordinationService;
import com.logvex.crystal.coordination.ModuleDependency;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the module compatibility engine over real (stub) modules and the live inspector. */
class ModuleCompatibilityEngineTest {

    private static final class StubModule extends Module {
        StubModule(String name) {
            super(Category.MISC, name, "");
        }

        StubModule needs(String other) {
            requires(ModuleDependency.module(other));
            return this;
        }
    }

    /** A dependency context where every named module is installed but none is enabled. */
    private static DependencyContext noneEnabled(ModuleManager manager) {
        return new DependencyContext() {
            @Override
            public boolean moduleInstalled(String name) {
                return manager.byName(name) != null;
            }

            @Override
            public boolean moduleEnabled(String name) {
                return false;
            }

            @Override
            public boolean serviceAvailable(String name) {
                return false;
            }

            @Override
            public boolean integrationAvailable(String name) {
                return false;
            }

            @Override
            public boolean capabilityAvailable(String name) {
                return false;
            }

            @Override
            public boolean modeActive(String mode) {
                return false;
            }
        };
    }

    @Test
    void everyModuleGetsAVerdictWithItsRequirements() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule("Base"));
        manager.register(new StubModule("Dependent").needs("Base"));
        manager.attachContext(null);
        ModuleCompatibilityEngine engine = new ModuleCompatibilityEngine(manager,
                new ModuleCoordinationService(manager, noneEnabled(manager), new ActionCoordinator()));

        List<CompatibilityVerdict> verdicts = engine.evaluateAll();
        assertEquals(2, verdicts.size());
        CompatibilityVerdict dependent = engine.byName().get("dependent");
        assertNotNull(dependent);
        assertTrue(dependent.requirements().stream().anyMatch(r -> r.contains("Base")),
                dependent.requirements().toString());
    }

    @Test
    void unknownModuleIsReportedAsAttentionNotCrash() {
        ModuleCompatibilityEngine engine = new ModuleCompatibilityEngine(null, null);
        CompatibilityVerdict verdict = engine.evaluate(null);
        assertFalse(verdict.operable());
        assertEquals(HealthStatus.ATTENTION, verdict.status());
    }

    @Test
    void summaryIsHumanReadableWhenNothingIsBlocked() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule("Solo"));
        manager.attachContext(null);
        ModuleCompatibilityEngine engine = new ModuleCompatibilityEngine(manager,
                new ModuleCoordinationService(manager, noneEnabled(manager), new ActionCoordinator()));
        assertTrue(engine.summary().contains("compatible"), engine.summary());
    }

    @Test
    void nullManagerYieldsNoVerdicts() {
        ModuleCompatibilityEngine engine = new ModuleCompatibilityEngine(null, null);
        assertTrue(engine.evaluateAll().isEmpty());
    }
}
