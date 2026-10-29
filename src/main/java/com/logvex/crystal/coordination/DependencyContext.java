package com.logvex.crystal.coordination;

/**
 * The environment a {@link ModuleDependency} is evaluated against.
 *
 * <p>Kept as a narrow interface so the dependency logic is Minecraft-independent and unit
 * testable with a stub: the live client backs it with the {@code ModuleManager}, the Baritone
 * availability probe and the capability table, while a test supplies plain booleans.
 *
 * <p>Every query is about something the client already knows locally. Nothing here contacts
 * the network or the server.
 */
public interface DependencyContext {

    /** True when a module with this name is registered (installed), regardless of its state. */
    boolean moduleInstalled(String name);

    /** True when a module with this name is registered and currently enabled. */
    boolean moduleEnabled(String name);

    /** True when a named shared service is present and usable (e.g. {@code "combat"}). */
    boolean serviceAvailable(String name);

    /** True when an optional integration is present and responsive (e.g. {@code "baritone"}). */
    boolean integrationAvailable(String name);

    /** True when a runtime capability is present (e.g. {@code "world-query"}). */
    boolean capabilityAvailable(String name);

    /** True when the named mutually-exclusive mode is the one currently active. */
    boolean modeActive(String mode);

    /**
     * A context where nothing is installed and no service, integration, capability or mode is
     * available. Used as a safe default and by tests that only care about module edges.
     */
    static DependencyContext empty() {
        return new DependencyContext() {
            @Override
            public boolean moduleInstalled(String name) {
                return false;
            }

            @Override
            public boolean moduleEnabled(String name) {
                return false;
            }

            @Override
            public boolean serviceAvailable(String name) {
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
}
