package com.logvex.crystal.commandcenter;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.command.CommandManager;
import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.HudEditorScreen;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import net.minecraft.client.MinecraftClient;

/**
 * The real {@link CommandCenterHost}: a thin adapter that maps each Command Center action onto
 * the client operation that already implements it.
 *
 * <p>It is deliberately tiny and free of policy - every method forwards to an existing service
 * or command. That keeps the Command Center a discovery/aggregation layer rather than a second
 * implementation of the client, and it means the unit tests can drive the actions through a
 * recording stub instead.
 */
public final class ClientCommandCenterHost implements CommandCenterHost {

    private final CrystalClient client;

    public ClientCommandCenterHost(CrystalClient client) {
        this.client = client;
    }

    @Override
    public void notify(String message, boolean error) {
        client.notify(message, error ? NotificationLevel.WARN : NotificationLevel.INFO);
    }

    @Override
    public void openGui(String view) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        ClickGui gui = client.clickGui();
        if (gui == null) {
            return;
        }
        if (view == null || view.isBlank()) {
            gui.open();
            return;
        }
        switch (view.toLowerCase(java.util.Locale.ROOT)) {
            case "appearance" -> gui.showAppearance();
            case "favorites" -> gui.showFavorites();
            case "recent" -> gui.showRecent();
            default -> gui.open();
        }
    }

    @Override
    public void openHudEditor() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient.getInstance().setScreen(new HudEditorScreen(client.hud(), client.theme()));
    }

    @Override
    public void saveAll() {
        client.saveAll();
    }

    @Override
    public void reloadAll() {
        client.reloadAll();
    }

    @Override
    public boolean toggleModule(String name) {
        Module module = client.modules().byName(name);
        if (module == null) {
            return false;
        }
        module.toggle(client.modules());
        return true;
    }

    @Override
    public boolean setModule(String name, boolean enabled) {
        Module module = client.modules().byName(name);
        if (module == null) {
            return false;
        }
        if (enabled) {
            module.enable(client.modules());
        } else {
            module.disable();
        }
        return true;
    }

    @Override
    public void printDiagnostics() {
        CommandManager commands = client.commands();
        if (commands != null) {
            commands.runFromHost("diag");
        }
    }

    @Override
    public void printConflicts() {
        CommandManager commands = client.commands();
        if (commands != null) {
            commands.runFromHost("conflicts");
        }
    }

    @Override
    public void printErrors(boolean clear) {
        CommandManager commands = client.commands();
        if (commands != null) {
            commands.runFromHost(clear ? "errors clear" : "errors");
        }
    }

    @Override
    public ClientStatusModel status() {
        return client.commandCenter().snapshot();
    }

    @Override
    public PerformanceReport performance() {
        return client.commandCenter().performance();
    }
}
