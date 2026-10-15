package com.logvex.crystal.gui;

import net.minecraft.client.MinecraftClient;

/**
 * Guards shared by every Crystal screen.
 *
 * <p>The client installs a loading {@code SplashOverlay} while it reloads resources.
 * During that window {@code MinecraftClient.textRenderer} is still null, and installing
 * a {@link net.minecraft.client.gui.screen.Screen} then leaves its {@code textRenderer}
 * field null too, so the first {@code drawText} throws a {@link NullPointerException}.
 * Screens must therefore not be installed, and must not draw, until the font renderer
 * exists and the overlay is gone.
 */
public final class ScreenGuard {

    private ScreenGuard() {
    }

    /** True once the loading overlay is gone and the font renderer has been built. */
    public static boolean fontsReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getOverlay() == null && client.textRenderer != null;
    }
}
