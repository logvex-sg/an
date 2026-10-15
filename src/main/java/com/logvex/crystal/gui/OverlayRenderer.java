package com.logvex.crystal.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A screen-space overlay drawn on top of the HUD, used by modules that need to paint
 * something beyond a fixed panel (direction indicators, search summaries).
 *
 * <p>Overlays are pure renderers like {@link HudElement}: they read state and draw.
 */
public interface OverlayRenderer {

    void renderOverlay(DrawContext context, MinecraftClient client, float tickDelta);
}
