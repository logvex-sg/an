package com.logvex.crystal.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Guards the real module registry: it must register exactly {@link ModuleRegistry#EXPECTED_MODULE_COUNT}
 * modules, and every registered module must be reachable by name.
 *
 * <p>{@link ModuleManagerTest} uses stub modules, so without this test a module could be
 * dropped from {@link ModuleRegistry} without any test noticing. Registration only reads
 * module metadata, so no Minecraft runtime is needed.
 */
class ModuleRegistryTest {

    @Test
    void registersExactlyTheExpectedCount() {
        ModuleManager manager = new ModuleManager();
        ModuleRegistry.registerAll(manager);
        assertEquals(ModuleRegistry.EXPECTED_MODULE_COUNT, manager.size(),
                "update EXPECTED_MODULE_COUNT when adding or removing a module");
    }

    @Test
    void everyRegisteredModuleIsReachableByName() {
        ModuleManager manager = new ModuleManager();
        ModuleRegistry.registerAll(manager);
        for (Module module : manager.all()) {
            assertNotNull(manager.byName(module.name()),
                    "module '" + module.name() + "' is not reachable by name");
        }
    }

    @Test
    void everyModuleHasAStableCategoryAndName() {
        ModuleManager manager = new ModuleManager();
        ModuleRegistry.registerAll(manager);
        for (Module module : manager.all()) {
            assertNotNull(module.name());
            assertNotNull(module.category());
        }
    }
}
