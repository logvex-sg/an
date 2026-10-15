package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies module-state persistence through the shared atomic IO and its backup. */
class ConfigManagerTest {

    private static final class StubModule extends Module {
        StubModule(String name) {
            super(Category.MISC, name, "");
        }
    }

    @Test
    void roundTripsEnabledStateAndKeybind(@TempDir Path dir) throws IOException {
        ModuleManager source = new ModuleManager();
        StubModule module = new StubModule("Alpha");
        source.register(module);
        module.enable(source);
        module.keybind().set(42);

        Path file = dir.resolve("modules.json");
        ConfigManager manager = new ConfigManager(source, file);
        manager.save();

        ModuleManager target = new ModuleManager();
        StubModule restored = new StubModule("Alpha");
        target.register(restored);
        new ConfigManager(target, file).load();

        assertTrue(restored.isEnabled());
        assertEquals(42, restored.keybind().key());
    }

    @Test
    void overwriteKeepsThePreviousFileAsBackup(@TempDir Path dir) throws IOException {
        ModuleManager manager = new ModuleManager();
        manager.register(new StubModule("Alpha"));
        Path file = dir.resolve("modules.json");
        ConfigManager config = new ConfigManager(manager, file);

        config.save();
        config.save();

        assertTrue(Files.exists(ConfigIo.backupPath(file)), "the second write backs up the first");
    }

    @Test
    void loadOfAnAbsentFileIsANoOp(@TempDir Path dir) {
        ModuleManager manager = new ModuleManager();
        StubModule module = new StubModule("Alpha");
        manager.register(module);
        new ConfigManager(manager, dir.resolve("missing.json")).load();
        assertFalse(module.isEnabled());
    }

    @Test
    void loadOfACorruptFileLeavesDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("modules.json");
        Files.writeString(file, "{ not json");
        ModuleManager manager = new ModuleManager();
        StubModule module = new StubModule("Alpha");
        manager.register(module);
        new ConfigManager(manager, file).load();
        assertFalse(module.isEnabled());
    }
}
