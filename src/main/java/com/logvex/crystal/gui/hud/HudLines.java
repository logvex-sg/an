package com.logvex.crystal.gui.hud;

import net.minecraft.client.MinecraftClient;

/** Supplies the current text lines for a {@link SimpleHudElement}. */
@FunctionalInterface
public interface HudLines {

    /** The lines to draw, or an empty array to draw nothing this frame. */
    String[] lines(MinecraftClient client);
}
