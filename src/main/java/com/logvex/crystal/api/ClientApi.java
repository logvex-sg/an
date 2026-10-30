package com.logvex.crystal.api;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.EventKind;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The read and act surface a first-party module or an extension is allowed to use.
 *
 * <p>This is the single entry point handed to extension code. It exposes only narrow,
 * documented operations; the concrete client internals (managers, the interaction controller,
 * raw game objects) stay behind it. Every registration returns an {@link ApiHandle} so the
 * caller - and the {@link ApiRegistry} - can release it deterministically.
 *
 * <p>Nothing here reaches the filesystem, the network or the JVM directly. An extension that
 * needs one of those must do it in its own code, which the client neither grants nor prevents;
 * see {@code docs/extension-security.md}.
 */
public interface ClientApi {

    /** The API version string this host implements, e.g. {@code 1.0}. */
    String apiVersion();

    /** The capabilities available right now. */
    Capabilities capabilities();

    /** True when the named capability is live. */
    default boolean supports(String capability) {
        return capabilities().has(capability);
    }

    /** A stable identifier for this client build, e.g. {@code 1.0.0}. */
    String clientVersion();

    // -------------------------------------------------------------- events

    /**
     * Subscribes to events of the given kinds. Returns a handle whose {@link ApiHandle#close()}
     * removes the subscription.
     */
    ApiHandle subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener);

    // ------------------------------------------------------------- modules

    /** Registers a module. Fails (and returns a closed handle) if the name is taken. */
    ApiHandle registerModule(com.logvex.crystal.module.Module module);

    // ------------------------------------------------------------- commands

    /**
     * Registers a chat command. {@code name} is matched case-insensitively after the {@code ;}
     * prefix. The handler receives the argument tokens; it returns true when it handled the
     * command. Registering a name already in use fails.
     */
    ApiHandle registerCommand(String name, java.util.function.Predicate<java.util.List<String>> handler);

    // ----------------------------------------------------------------- hud

    /** Registers a HUD element. */
    ApiHandle registerHudElement(com.logvex.crystal.gui.HudElement element);

    // ---------------------------------------------------------------- misc

    /** Posts a notification. */
    void notify(String message, String level);

    /** Records a message in the client's diagnostics log under this owner's name. */
    void log(String message);
}
