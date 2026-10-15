package com.logvex.crystal.module;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.setting.KeybindSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and lifecycle owner for every module.
 *
 * <p>Responsibilities: registration by name with duplicate detection, ordered and
 * exception-isolated ticking, dependency/conflict resolution, keybind dispatch and
 * disconnect handling.
 */
public final class ModuleManager {

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final List<Module> ordered = new ArrayList<>();

    public void register(Module module) {
        String key = module.name().toLowerCase();
        if (modules.containsKey(key)) {
            throw new IllegalStateException("Duplicate module name: " + module.name());
        }
        modules.put(key, module);
        ordered.add(module);
    }

    /** Injects the shared service context into every registered module. */
    public void attachContext(ModuleContext context) {
        for (Module module : ordered) {
            module.attach(context);
        }
    }

    public Module byName(String name) {
        return name == null ? null : modules.get(name.toLowerCase());
    }

    public <M extends Module> M byClass(Class<M> type) {
        for (Module module : ordered) {
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        return null;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(ordered);
    }

    public List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : ordered) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public int enabledCount() {
        int count = 0;
        for (Module module : ordered) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public int size() {
        return ordered.size();
    }

    // ------------------------------------------------------------ dependencies

    /** Returns the name of the first unsatisfied dependency, or null when all are met. */
    public String firstMissingDependency(Module module) {
        for (String dependency : module.dependencies()) {
            Module other = byName(dependency);
            if (other == null) {
                return dependency + " (not installed)";
            }
            if (!other.isEnabled()) {
                return dependency + " (disabled)";
            }
        }
        return null;
    }

    /** Returns the first enabled module that conflicts with {@code module}, or null. */
    public Module firstActiveConflict(Module module) {
        for (String conflict : module.conflicts()) {
            Module other = byName(conflict);
            if (other != null && other.isEnabled()) {
                return other;
            }
        }
        return null;
    }

    public boolean canEnable(Module module) {
        return firstMissingDependency(module) == null && firstActiveConflict(module) == null;
    }

    // ------------------------------------------------------------ lifecycle

    public void tickAll() {
        for (Module module : ordered) {
            if (module.isEnabled()) {
                module.tickSafely();
            }
        }
    }

    public void onDisconnect() {
        for (Module module : ordered) {
            try {
                module.onDisconnect();
            } catch (Throwable t) {
                CrystalClient.LOG.error("Module {} failed during disconnect handling", module.name(), t);
            }
        }
    }

    /** Enables every module in the given profile state, respecting dependencies by retry. */
    public void applyEnabledStates(Map<String, Boolean> states) {
        List<Module> pending = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            Module module = byName(entry.getKey());
            if (module == null) {
                CrystalClient.LOG.warn("Config references unknown module '{}'", entry.getKey());
                continue;
            }
            if (entry.getValue()) {
                pending.add(module);
            } else if (!module.isAlwaysEnabled()) {
                module.disable();
            }
        }
        // Retry a few passes so dependencies enabled later in the list still resolve.
        for (int pass = 0; pass < 3 && !pending.isEmpty(); pass++) {
            pending.removeIf(module -> module.isEnabled() || module.enable(this));
        }
        for (Module module : pending) {
            CrystalClient.LOG.warn("Could not enable module '{}': {}", module.name(), module.lastError());
        }
    }

    // ------------------------------------------------------------ keybinds

    /** Handles a GLFW key press: toggles any module bound to that key. */
    public void onKeyPressed(int keyCode) {
        if (keyCode == KeybindSetting.UNBOUND) {
            return;
        }
        for (Module module : ordered) {
            KeybindSetting bind = module.keybind();
            if (bind.isBound() && bind.key() == keyCode && !module.isAlwaysEnabled()) {
                module.toggle(this);
            }
        }
    }
}
