package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists the module state (enabled flags, keybinds and setting values) to
 * {@code <config>/modules.json}, with atomic writes and a schema version.
 *
 * <p>Only the module layer is stored here; the appearance lives in {@link AppearanceStore}
 * so a theme change never rewrites module config. The codec is Minecraft-independent
 * apart from the module classes it reflects over.
 */
public final class ConfigManager {

    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final ModuleManager modules;
    private final Path file;

    public ConfigManager(ModuleManager modules, Path file) {
        this.modules = modules;
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Writes the current module state atomically. */
    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject moduleObject = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            JsonObject settings = new JsonObject();
            settings.addProperty("keybind", module.keybind().key());
            for (SettingGroup group : module.groups()) {
                for (Setting<?> setting : group.settings()) {
                    if (setting == module.keybind()) {
                        continue;
                    }
                    settings.addProperty(group.name() + "." + setting.name(), setting.toJson());
                }
            }
            entry.add("settings", settings);
            moduleObject.add(module.name(), entry);
        }
        root.add("modules", moduleObject);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Loads module state from disk, applying values and enabled flags. */
    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                apply(root.getAsJsonObject());
            }
        } catch (Exception e) {
            // Corrupt config: leave the client at defaults.
        }
    }

    /** Applies a parsed config object to the live modules. Exposed for tests. */
    public void apply(JsonObject root) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (!root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }
        Map<String, Boolean> enabledStates = new LinkedHashMap<>();
        JsonObject moduleObject = root.getAsJsonObject("modules");
        for (String name : moduleObject.keySet()) {
            Module module = modules.byName(name);
            if (module == null) {
                continue;
            }
            JsonObject entry = moduleObject.getAsJsonObject(name);
            if (entry.has("settings") && entry.get("settings").isJsonObject()) {
                applySettings(module, entry.getAsJsonObject("settings"));
            }
            if (entry.has("enabled") && entry.get("enabled").isJsonPrimitive()) {
                enabledStates.put(module.name(), entry.get("enabled").getAsBoolean());
            }
        }
        modules.applyEnabledStates(enabledStates);
    }

    private void applySettings(Module module, JsonObject settings) {
        for (String key : settings.keySet()) {
            JsonElement value = settings.get(key);
            if (key.equals("keybind")) {
                module.keybind().fromJson(value.getAsString());
                continue;
            }
            Setting<?> setting = findSetting(module, key);
            if (setting != null && value.isJsonPrimitive()) {
                setting.fromJson(value.getAsString());
            }
        }
    }

    private Setting<?> findSetting(Module module, String key) {
        for (SettingGroup group : module.groups()) {
            for (Setting<?> setting : group.settings()) {
                if (setting == module.keybind()) {
                    continue;
                }
                if ((group.name() + "." + setting.name()).equals(key)) {
                    return setting;
                }
            }
        }
        return null;
    }
}
