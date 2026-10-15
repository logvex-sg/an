package com.logvex.crystal.module;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.ColorSetting;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.KeybindSetting;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.setting.StringSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for every feature. A module owns its settings, its enable/disable lifecycle
 * and its dependency/conflict metadata.
 *
 * <p>Lifecycle contract:
 * <ul>
 *   <li>{@link #onEnable()} runs on the client thread after {@code enabled} flips true.</li>
 *   <li>{@link #onDisable()} runs on the client thread after {@code enabled} flips false.
 *       It must release every resource the module acquired.</li>
 *   <li>{@link #onTick()} runs once per client tick while enabled, on the client thread.</li>
 * </ul>
 *
 * <p>Modules never mutate world state directly; they go through the shared services.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private final KeybindSetting keybind;

    private final List<SettingGroup> groups = new ArrayList<>();
    private final List<SettingGroup> groupsView = Collections.unmodifiableList(groups);
    private final List<String> dependencies = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();

    private boolean enabled;
    private long enabledAtMillis;
    private long lastTickNanos;
    private String lastError;
    private boolean alwaysEnabled;
    private ModuleContext context;

    protected Module(Category category, String name, String description) {
        this.category = Objects.requireNonNull(category);
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.keybind = new KeybindSetting("Keybind", "Toggles this module.", KeybindSetting.UNBOUND, true);
    }

    /** Injected by {@link ModuleManager#attachContext} after registration, before any tick. */
    public final void attach(ModuleContext context) {
        this.context = context;
    }

    /** The shared services. Non-null once the client has finished bootstrapping. */
    protected final ModuleContext context() {
        return context;
    }

    // ---------------------------------------------------------------- identity

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final KeybindSetting keybind() {
        return keybind;
    }

    /** Lower-case search key used by the ClickGUI search box. */
    public final String searchKey() {
        return name.toLowerCase();
    }

    // ---------------------------------------------------------------- settings

    /** Adds an ungrouped setting. */
    protected final <S extends Setting<?>> S add(S setting) {
        return add((String) null, setting);
    }

    /** Adds an ungrouped setting under the given group name. */
    protected final <S extends Setting<?>> S add(String groupName, S setting) {
        if (groupName == null) {
            groupName = "General";
        }
        SettingGroup group = groupFor(groupName);
        group.add(setting);
        return setting;
    }

    // Convenience constructors, so a module declaration reads as one line per setting.

    protected final BooleanSetting add(String name, String description, boolean defaultValue) {
        return add(new BooleanSetting(name, description, defaultValue));
    }

    protected final IntSetting add(String name, String description, int defaultValue, int min, int max) {
        return add(new IntSetting(name, description, defaultValue, min, max));
    }

    protected final DoubleSetting add(String name, String description, double defaultValue, double min, double max) {
        return add(new DoubleSetting(name, description, defaultValue, min, max));
    }

    protected final StringSetting add(String name, String description, String defaultValue) {
        return add(new StringSetting(name, description, defaultValue, 256));
    }

    protected final ColorSetting add(String name, String description, int defaultArgb, boolean color) {
        return add(new ColorSetting(name, description, defaultArgb));
    }

    protected final <E extends Enum<E>> EnumSetting<E> add(String name, String description, E defaultValue) {
        return add(new EnumSetting<>(name, description, defaultValue));
    }

    private SettingGroup groupFor(String name) {
        for (SettingGroup group : groups) {
            if (group.name().equals(name)) {
                return group;
            }
        }
        SettingGroup created = new SettingGroup(name);
        groups.add(created);
        return created;
    }

    public final List<SettingGroup> groups() {
        return groupsView;
    }

    /** Declares a module that must be enabled before this one can be. */
    protected final void dependsOn(String moduleName) {
        dependencies.add(moduleName);
    }

    /** Declares a module that must not be enabled at the same time as this one. */
    protected final void conflictsWith(String moduleName) {
        conflicts.add(moduleName);
    }

    public final List<String> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public final List<String> conflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    /** Marks a module as always active; it cannot be toggled off and ignores its keybind. */
    protected final void alwaysEnabled() {
        this.alwaysEnabled = true;
        this.enabled = true;
    }

    public final boolean isAlwaysEnabled() {
        return alwaysEnabled;
    }

    // ---------------------------------------------------------------- lifecycle

    public final boolean isEnabled() {
        return enabled;
    }

    /** Milliseconds since this module was enabled, or 0 when disabled. */
    public final long enabledMillis() {
        return enabled ? System.currentTimeMillis() - enabledAtMillis : 0;
    }

    public final long lastTickNanos() {
        return lastTickNanos;
    }

    public final String lastError() {
        return lastError;
    }

    /**
     * Enables the module. Returns false (without side effects) if a dependency is missing
     * or a conflict is active; the caller decides how to report that.
     */
    public final boolean enable(ModuleManager manager) {
        if (enabled) {
            return true;
        }
        String missing = manager.firstMissingDependency(this);
        if (missing != null) {
            lastError = "Missing dependency: " + missing;
            return false;
        }
        Module conflict = manager.firstActiveConflict(this);
        if (conflict != null) {
            lastError = "Conflicts with enabled module: " + conflict.name();
            return false;
        }
        enabled = true;
        enabledAtMillis = System.currentTimeMillis();
        lastError = null;
        try {
            onEnable();
        } catch (Throwable t) {
            enabled = false;
            lastError = t.toString();
            CrystalClient.LOG.error("Module {} failed to enable", name, t);
            return false;
        }
        return true;
    }

    public final void disable() {
        if (!enabled || alwaysEnabled) {
            return;
        }
        enabled = false;
        try {
            onDisable();
        } catch (Throwable t) {
            lastError = t.toString();
            CrystalClient.LOG.error("Module {} failed to disable cleanly", name, t);
        }
    }

    public final void toggle(ModuleManager manager) {
        if (enabled) {
            disable();
        } else {
            enable(manager);
        }
    }

    /** Ticks the module, isolating exceptions so one broken module cannot stall the loop. */
    public final void tickSafely() {
        if (!enabled) {
            return;
        }
        long start = System.nanoTime();
        try {
            onTick();
        } catch (Throwable t) {
            lastError = t.toString();
            CrystalClient.LOG.error("Module {} threw during tick", name, t);
        } finally {
            lastTickNanos = System.nanoTime() - start;
        }
    }

    /** Called when the client disconnects from a world; default is to disable. */
    public void onDisconnect() {
        if (enabled && !alwaysEnabled) {
            disable();
        }
    }

    // ---------------------------------------------------------------- hooks

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick() {
    }

    @Override
    public String toString() {
        return name + (enabled ? " [on]" : "");
    }
}
