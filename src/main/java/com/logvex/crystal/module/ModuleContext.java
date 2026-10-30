package com.logvex.crystal.module;

import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.coordination.ActionCoordinator;
import com.logvex.crystal.gui.HudManager;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.theme.ThemeManager;

/**
 * The services a module is allowed to reach. Modules receive this after registration
 * rather than constructing their own dependencies, which keeps the dependency graph
 * acyclic (services never depend on modules) and makes modules testable with a stub.
 */
public final class ModuleContext {

    private final ModuleManager moduleManager;
    private final CombatService combatService;
    private final ThemeManager theme;
    private final HudManager hudManager;
    private final NotificationManager notifications;
    private final ConfigManager configManager;
    private final ActionCoordinator actionCoordinator;

    public ModuleContext(ModuleManager moduleManager, CombatService combatService, ThemeManager theme,
                         HudManager hudManager, NotificationManager notifications, ConfigManager configManager) {
        this(moduleManager, combatService, theme, hudManager, notifications, configManager,
                new ActionCoordinator());
    }

    public ModuleContext(ModuleManager moduleManager, CombatService combatService, ThemeManager theme,
                         HudManager hudManager, NotificationManager notifications, ConfigManager configManager,
                         ActionCoordinator actionCoordinator) {
        this.moduleManager = moduleManager;
        this.combatService = combatService;
        this.theme = theme;
        this.hudManager = hudManager;
        this.notifications = notifications;
        this.configManager = configManager;
        this.actionCoordinator = actionCoordinator == null ? new ActionCoordinator() : actionCoordinator;
    }

    public ModuleManager moduleManager() {
        return moduleManager;
    }

    public CombatService combatService() {
        return combatService;
    }

    public ThemeManager theme() {
        return theme;
    }

    public HudManager hudManager() {
        return hudManager;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * The shared action arbitration layer. Modules that perform a client action (place, break,
     * attack, inventory move) should submit it here rather than driving the interaction manager
     * directly, so two modules cannot act on the same input in the same tick.
     */
    public ActionCoordinator actions() {
        return actionCoordinator;
    }
}
