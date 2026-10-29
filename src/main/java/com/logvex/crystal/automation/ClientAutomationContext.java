package com.logvex.crystal.automation;

import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.combat.CombatTarget;
import com.logvex.crystal.config.ProfileService;
import com.logvex.crystal.context.ContextProfileService;
import com.logvex.crystal.coordination.ActionCoordinator;
import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import net.minecraft.client.MinecraftClient;

/**
 * The live {@link AutomationContext}: it answers the engine's questions from the running
 * client and performs the engine's effects through the existing services.
 *
 * <p>It holds no state of its own beyond the references it needs, so an automation rule can
 * only ever reach the small, named set of operations the interface exposes. Enabling a module
 * goes through {@link Module#enable}, disabling through {@link Module#disable}, a route
 * cancellation through {@link BaritoneService#cancel}; nothing here bypasses those paths.
 */
public final class ClientAutomationContext implements AutomationContext {

    private final ModuleManager modules;
    private final CombatService combat;
    private final BaritoneService baritone;
    private final ProfileService profiles;
    private final ContextProfileService contextProfiles;
    private final NotificationManager notifications;
    private final ModuleErrorLog errors;
    private final ActionCoordinator actions;

    public ClientAutomationContext(ModuleManager modules, CombatService combat, BaritoneService baritone,
                                   ProfileService profiles, ContextProfileService contextProfiles,
                                   NotificationManager notifications, ModuleErrorLog errors,
                                   ActionCoordinator actions) {
        this.modules = modules;
        this.combat = combat;
        this.baritone = baritone;
        this.profiles = profiles;
        this.contextProfiles = contextProfiles;
        this.notifications = notifications;
        this.errors = errors;
        this.actions = actions;
    }

    @Override
    public boolean playerAvailable() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null;
    }

    @Override
    public boolean worldAvailable() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.world != null && client.player != null;
    }

    @Override
    public boolean moduleInstalled(String name) {
        return modules != null && modules.byName(name) != null;
    }

    @Override
    public boolean moduleEnabled(String name) {
        if (modules == null) {
            return false;
        }
        Module module = modules.byName(name);
        return module != null && module.isEnabled();
    }

    @Override
    public boolean integrationAvailable(String name) {
        return name != null && name.equalsIgnoreCase("baritone") && baritone != null && baritone.available();
    }

    @Override
    public String activeProfile() {
        return contextProfiles == null ? "" : contextProfiles.appliedProfile();
    }

    @Override
    public double healthFraction() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.player.getMaxHealth() <= 0) {
            return 1.0;
        }
        return client.player.getHealth() / client.player.getMaxHealth();
    }

    @Override
    public double targetDistance() {
        CombatTarget target = combat == null ? null : combat.currentTarget();
        if (target == null) {
            return -1;
        }
        return target.distance();
    }

    @Override
    public boolean actionIdle() {
        return actions == null || actions.activeCount() == 0;
    }

    @Override
    public boolean enableModule(String name) {
        if (modules == null) {
            return false;
        }
        Module module = modules.byName(name);
        if (module == null) {
            return false;
        }
        if (module.isEnabled()) {
            return true;
        }
        return module.enable(modules);
    }

    @Override
    public boolean disableModule(String name) {
        if (modules == null) {
            return false;
        }
        Module module = modules.byName(name);
        if (module == null) {
            return false;
        }
        if (!module.isEnabled()) {
            return true;
        }
        module.disable();
        return !module.isEnabled();
    }

    @Override
    public boolean switchProfile(String name) {
        return profiles != null && profiles.apply(name);
    }

    @Override
    public boolean cancelRoute() {
        if (baritone == null || !baritone.available()) {
            return false;
        }
        baritone.cancel();
        return true;
    }

    @Override
    public void notify(String message, String level) {
        if (notifications == null) {
            return;
        }
        notifications.notify(message, parseLevel(level));
    }

    @Override
    public void log(String message) {
        if (errors != null) {
            errors.record("Automation", message);
        }
    }

    private static NotificationLevel parseLevel(String level) {
        if (level == null) {
            return NotificationLevel.INFO;
        }
        return switch (level.toLowerCase(java.util.Locale.ROOT)) {
            case "success" -> NotificationLevel.SUCCESS;
            case "warn", "warning" -> NotificationLevel.WARN;
            case "error" -> NotificationLevel.ERROR;
            case "cute" -> NotificationLevel.CUTE;
            default -> NotificationLevel.INFO;
        };
    }
}
