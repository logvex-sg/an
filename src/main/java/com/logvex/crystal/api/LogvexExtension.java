package com.logvex.crystal.api;

/**
 * The entry point an extension implements. An extension is an ordinary Fabric mod that declares
 * this as a {@code logvex-crystal-extension} entrypoint and depends on {@code logvex-crystal};
 * the client loads the entrypoint with Fabric's standard mechanism and calls
 * {@link #initialize(ClientApi)} once, on the client thread, after the core services exist.
 *
 * <p>There is deliberately no custom classloader and no "unload the code" step: Fabric loads a
 * mod's classes once for the game's lifetime. {@link #shutdown()} releases the <em>registrations</em>
 * an extension made (its listeners, commands, modules, HUD elements) so they stop acting, but
 * the classes themselves remain loaded. This limitation is documented rather than hidden; see
 * {@code docs/extension-security.md}.
 */
public interface LogvexExtension {

    /**
     * Called once when the extension is activated. The {@link ClientApi} is scoped to this
     * extension: every handle it returns is released automatically when the extension is
     * disabled or the client shuts down.
     *
     * <p>Throw to signal a failed initialization; the client rolls back any registrations made
     * before the throw and marks the extension failed. Never assume {@code initialize} is called
     * more than once.
     */
    void initialize(ClientApi api);

    /**
     * Called when the extension is deactivated or the client shuts down, after its
     * registrations have been released. Use it to stop any background work the extension
     * started itself. Default is a no-op.
     */
    default void shutdown() {
    }
}
