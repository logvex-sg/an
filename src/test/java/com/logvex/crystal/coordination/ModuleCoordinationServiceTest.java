package com.logvex.crystal.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import org.junit.jupiter.api.Test;

/** Covers the coordination inspector over real (stub) modules. */
class ModuleCoordinationServiceTest {

    private static final class StubModule extends Module {
        StubModule(String name) {
            super(Category.COMBAT, name, "");
        }

        StubModule needs(String other) {
            requires(ModuleDependency.module(other));
            return this;
        }

        StubModule excludes(String other) {
            conflictsWith(other);
            return this;
        }
    }

    /** A context where the named modules are installed and enabled. */
    private static DependencyContext allEnabled(ModuleManager manager, String... extraServices) {
        return new DependencyContext() {
            @Override
            public boolean moduleInstalled(String name) {
                return manager.byName(name) != null;
            }

            @Override
            public boolean moduleEnabled(String name) {
                Module module = manager.byName(name);
                return module != null && module.isEnabled();
            }

            @Override
            public boolean serviceAvailable(String name) {
                for (String service : extraServices) {
                    if (service.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
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
    void blockerIsReportedWhenDependencyDisabled() {
        ModuleManager manager = new ModuleManager();
        StubModule base = new StubModule("Base");
        StubModule dependent = new StubModule("Dependent").needs("Base");
        manager.register(base);
        manager.register(dependent);
        manager.attachContext(null);

        ModuleCoordinationService service = new ModuleCoordinationService(manager,
                allEnabled(manager), new ActionCoordinator());

        String blocker = service.blockerFor(dependent);
        assertNotNull(blocker);
        assertTrue(blocker.contains("Base"));
        assertFalse(service.canOperate(dependent));
        assertNull(service.blockerFor(base));
    }

    @Test
    void dependencySatisfiedOnceEnabled() {
        ModuleManager manager = new ModuleManager();
        StubModule base = new StubModule("Base");
        StubModule dependent = new StubModule("Dependent").needs("Base");
        manager.register(base);
        manager.register(dependent);

        ModuleCoordinationService service = new ModuleCoordinationService(manager,
                allEnabled(manager), new ActionCoordinator());
        assertFalse(service.canOperate(dependent));

        base.enable(manager);
        service.rebuild();
        assertTrue(service.canOperate(dependent));
    }

    @Test
    void inspectReportsActiveConflictBetweenEnabledModules() {
        ModuleManager manager = new ModuleManager();
        // One-directional declaration: the enable guard lets A declare a conflict and still
        // be enabled alongside B when B does not reciprocate, which is exactly the state the
        // inspector must flag (it can also arise from a profile applied in a given order).
        StubModule a = new StubModule("Walk").excludes("Stop");
        StubModule b = new StubModule("Stop");
        manager.register(a);
        manager.register(b);
        assertTrue(a.enable(manager));
        assertTrue(b.enable(manager));

        ModuleCoordinationService service = new ModuleCoordinationService(manager,
                allEnabled(manager), new ActionCoordinator());
        ConflictReport report = service.inspect();
        assertFalse(report.clean());
        assertTrue(report.count(ConflictReport.Severity.WARNING) >= 1);
        assertTrue(service.conflictedModules().contains("Walk"));
    }

    @Test
    void inspectReportsMissingTypedDependency() {
        ModuleManager manager = new ModuleManager();
        StubModule dependent = new StubModule("NeedsCombat").needs("Combat");
        manager.register(dependent);

        ModuleCoordinationService service = new ModuleCoordinationService(manager,
                DependencyContext.empty(), new ActionCoordinator());
        ConflictReport report = service.inspect();
        assertTrue(report.count(ConflictReport.Severity.BLOCKING) >= 1);
    }

    @Test
    void cleanReportWhenEverythingSatisfied() {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule("Solo"));
        ModuleCoordinationService service = new ModuleCoordinationService(manager,
                allEnabled(manager), new ActionCoordinator());
        assertTrue(service.inspect().clean());
        assertEquals(1, service.operableModules().size());
    }
}
