package com.logvex.crystal.module.misc;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * AutoReconnect: after an unexpected disconnect, waits a short delay and reconnects to the
 * same server.
 *
 * <p>The last server is captured while connected, because
 * {@code MinecraftClient.getCurrentServerEntry()} returns null once the disconnect has
 * completed. Reconnection uses vanilla's own connect path, so it behaves exactly as if the
 * player had clicked the server again. A single-player world or a disconnect with no known
 * server is left alone.
 */
public final class AutoReconnectModule extends Module {

    private final IntSetting delaySeconds = add("DelaySeconds", "Seconds to wait before reconnecting.", 5, 1, 60);
    private final IntSetting maxAttempts = add("MaxAttempts", "Give up after this many failed attempts.", 3, 1, 10);

    private ServerInfo lastServer;
    private boolean wasConnected;
    private long reconnectAt;
    private int attempts;

    public AutoReconnectModule() {
        super(Category.MISC, "AutoReconnect", "Reconnects to the last server after a disconnect.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean connected = client.getNetworkHandler() != null && client.player != null;
        if (connected) {
            ServerInfo current = client.getCurrentServerEntry();
            if (current != null) {
                lastServer = current;
            }
            wasConnected = true;
            reconnectAt = 0;
            attempts = 0;
            return;
        }
        if (!wasConnected || lastServer == null || client.isInSingleplayer()) {
            return;
        }
        if (reconnectAt == 0) {
            reconnectAt = System.currentTimeMillis() + delaySeconds.value() * 1000L;
            context().notifications().warn("Disconnected \u2014 reconnecting in "
                    + delaySeconds.value() + "s \u2661");
            return;
        }
        if (System.currentTimeMillis() < reconnectAt) {
            return;
        }
        reconnectAt = 0;
        if (attempts >= maxAttempts.value()) {
            wasConnected = false;
            context().notifications().error("Reconnect gave up after " + attempts + " attempts");
            return;
        }
        attempts++;
        connect(client, lastServer);
    }

    private void connect(MinecraftClient client, ServerInfo info) {
        try {
            net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(
                    client.currentScreen, client, ServerAddress.parse(info.address), info, false, null);
        } catch (Throwable t) {
            // A failed connect is expected sometimes (server full, offline); report it with a
            // stack trace and tell the user rather than swallowing it.
            reportError("reconnect attempt failed", t);
            context().notifications().error("Reconnect failed: " + t.getMessage());
        }
    }

    @Override
    protected void onDisable() {
        wasConnected = false;
        reconnectAt = 0;
        attempts = 0;
    }

    public ServerInfo lastServer() {
        return lastServer;
    }

    public int attempts() {
        return attempts;
    }
}
