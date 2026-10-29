package com.logvex.crystal.api;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.EventKind;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The concrete client operations {@link ApiRegistry} delegates to, behind an interface.
 *
 * <p>The registry only owns <em>scoping and cleanup</em>; the actual work of subscribing to the
 * event bus, registering a module, command or HUD element is done here. Keeping it an interface
 * means the ownership and rollback rules can be unit tested with a recording fake, and the
 * real implementation ({@link ClientApiServices}) is thin glue over the client managers.
 *
 * <p>Every method returns an {@link AutoCloseable} that undoes the registration; a method throws
 * (rather than returning a dead handle) when the registration is rejected, so the registry can
 * report the reason to the extension.
 */
public interface ApiServices {

    /** Subscribes to the event bus; the closer removes the subscription. */
    AutoCloseable subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener);

    /** Registers a module and attaches the shared context; the closer disables and removes it. */
    AutoCloseable registerModule(Module module);

    /** Registers a chat command; the closer unregisters it. Throws when the name is taken. */
    AutoCloseable registerCommand(String name, Predicate<List<String>> handler);

    /** Registers a HUD element; the closer removes it. */
    AutoCloseable registerHudElement(HudElement element);

    /** Posts a notification at the given level. */
    void notify(String message, NotificationLevel level);

    /** Records a message in the diagnostics log under the given owner name. */
    void recordError(String owner, String message);
}
