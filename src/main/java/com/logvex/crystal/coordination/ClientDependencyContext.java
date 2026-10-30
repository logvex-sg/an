package com.logvex.crystal.coordination;

import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The live {@link DependencyContext}: it answers the dependency graph's questions from the
 * running client.
 *
 * <p>Module state comes from the {@link ModuleManager}; integrations are supplied as named
 * probes (so the coordination layer never imports Baritone); services and capabilities are
 * registered as fixed facts. Everything is local, read-only and cheap enough to evaluate on
 * demand.
 */
public final class ClientDependencyContext implements DependencyContext {

    private final ModuleManager modules;
    private final Set<String> services = new LinkedHashSet<>();
    private final Set<String> capabilities = new LinkedHashSet<>();
    private final java.util.Map<String, BooleanSupplier> integrations = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, BooleanSupplier> modes = new java.util.LinkedHashMap<>();

    public ClientDependencyContext(ModuleManager modules) {
        this.modules = modules;
    }

    /** Registers a shared service as available. */
    public ClientDependencyContext service(String name) {
        services.add(name);
        return this;
    }

    /** Registers a runtime capability as available. */
    public ClientDependencyContext capability(String name) {
        capabilities.add(name);
        return this;
    }

    /** Registers an optional integration and a live availability probe. */
    public ClientDependencyContext integration(String name, BooleanSupplier available) {
        integrations.put(name, available);
        return this;
    }

    /** Registers a mutually-exclusive mode and a probe for whether it is currently active. */
    public ClientDependencyContext mode(String name, BooleanSupplier active) {
        modes.put(name, active);
        return this;
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
    public boolean serviceAvailable(String name) {
        return services.contains(name);
    }

    @Override
    public boolean integrationAvailable(String name) {
        BooleanSupplier probe = integrations.get(name);
        if (probe == null) {
            return false;
        }
        try {
            return probe.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean capabilityAvailable(String name) {
        return capabilities.contains(name);
    }

    @Override
    public boolean modeActive(String name) {
        BooleanSupplier probe = modes.get(name);
        if (probe == null) {
            return false;
        }
        try {
            return probe.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
