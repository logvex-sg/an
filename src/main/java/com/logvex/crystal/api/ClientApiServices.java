package com.logvex.crystal.api;

import com.logvex.crystal.command.CommandManager;
import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.ClientEventBus;
import com.logvex.crystal.event.EventKind;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.gui.HudManager;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleContext;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The real {@link ApiServices}: thin glue that turns each API call into the matching call on the
 * client's managers.
 *
 * <p>Kept deliberately thin - all the ownership, rollback and cleanup logic lives in
 * {@link ApiRegistry}, which is tested against a fake of this interface. Anything with a branch
 * or a rule belongs there, not here.
 */
public final class ClientApiServices implements ApiServices {

    private final ClientEventBus bus;
    private final ModuleManager modules;
    private final CommandManager commands;
    private final HudManager hud;
    private final NotificationManager notifications;
    private final ModuleErrorLog errors;
    private final ModuleContext moduleContext;

    public ClientApiServices(ClientEventBus bus, ModuleManager modules, CommandManager commands,
                             HudManager hud, NotificationManager notifications, ModuleErrorLog errors,
                             ModuleContext moduleContext) {
        this.bus = bus;
        this.modules = modules;
        this.commands = commands;
        this.hud = hud;
        this.notifications = notifications;
        this.errors = errors;
        this.moduleContext = moduleContext;
    }

    @Override
    public AutoCloseable subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener) {
        ClientEventBus.Subscription subscription = bus.subscribe(kinds, listener::accept);
        return subscription::close;
    }

    @Override
    public AutoCloseable registerModule(Module module) {
        modules.register(module);
        module.attach(moduleContext);
        return () -> {
            module.disable();
            modules.remove(module.name());
        };
    }

    @Override
    public AutoCloseable registerCommand(String name, Predicate<List<String>> handler) {
        Runnable unregister = commands.registerDynamic(name, handler);
        return unregister::run;
    }

    @Override
    public AutoCloseable registerHudElement(HudElement element) {
        hud.register(element);
        return () -> hud.removeElement(element.id());
    }

    @Override
    public void notify(String message, NotificationLevel level) {
        notifications.notify(message, level);
    }

    @Override
    public void recordError(String owner, String message) {
        errors.record(owner, message);
    }
}
