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
                CrystalClient.errors().record(module.name(), t);
            }
        }
    }

    /**
     * Releases every module's resources at client shutdown. Isolated per module so a single
     * failure cannot leave later modules' workers running.
     */
    public void shutdownAll() {
        for (Module module : ordered) {
            module.shutdown();
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

    // ------------------------------------------------------------ keybind conflicts

    /**
     * Every module whose keybind is {@code key}, in registration order. Empty when the key is
     * unbound or held by nobody. The first entry is the "owner" the resolver keeps.
     */
    public List<Module> boundTo(int key) {
        List<Module> result = new ArrayList<>();
        if (key == KeybindSetting.UNBOUND) {
            return result;
        }
        for (Module module : ordered) {
            if (module.keybind().isBound() && module.keybind().key() == key) {
                result.add(module);
            }
        }
        return result;
    }

    /** The other modules sharing {@code module}'s key (excluding the module itself). */
    public List<Module> conflictsOf(Module module) {
        List<Module> result = new ArrayList<>();
        int key = module.keybind().key();
        if (!module.keybind().isBound()) {
            return result;
        }
        for (Module other : ordered) {
            if (other != module && other.keybind().isBound() && other.keybind().key() == key) {
                result.add(other);
            }
        }
        return result;
    }

    /** True when more than one module is bound to the same key. */
    public boolean hasKeybindConflicts() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Module module : ordered) {
            int key = module.keybind().key();
            if (module.keybind().isBound() && !seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives {@code module} the key exclusively, unbinding every other module that held it.
     *
     * <p>Two modules on one key would both toggle from a single press, which is never what
     * the user wants; this makes "last assignment wins" explicit instead of silently
     * double-firing. The unbinds fire the settings' change hooks, so the GUI and the autosave
     * both see them.
     *
     * @return the number of other modules that were unbound
     */
    public int resolveKeybindConflict(Module module, int key) {
        if (key == KeybindSetting.UNBOUND) {
            return 0;
        }
        int cleared = 0;
        for (Module other : ordered) {
            if (other != module && other.keybind().isBound() && other.keybind().key() == key) {
                other.keybind().unbind();
                cleared++;
            }
        }
        return cleared;
    }
}
