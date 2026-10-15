package com.logvex.crystal.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A single HUD panel. Elements are pure renderers: they read live state and draw it,
 * never mutate anything. Keeping them side-effect free is what lets the HUD run on the
 * render thread without coordination.
 *
 * <p>Positioning is anchor-based: an element declares a column and an offset, and the
 * {@link HudManager} stacks it. Elements also expose a preferred size so the drag editor
 * can hit-test them.
 */
public interface HudElement {

    /** Stable identifier used by the manager for enable/disable, ordering and persistence. */
    String id();

    /** Which screen edge the element stacks against. */
    Column column();

    /**
     * Renders the element.
     *
     * @param x            anchor x (left edge for LEFT, right edge for RIGHT)
     * @param y            top edge of the panel
     * @param rightAligned whether text should be right-aligned to {@code x}
     */
    void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned);

    /** Approximate height in pixels, used to stack elements in a column. */
    int height(MinecraftClient client);

    /** Approximate width in pixels, used for hit-testing in the drag editor. */
    default int width(MinecraftClient client) {
        return 80;
    }

    /** Whether the element is a draggable, positionable panel (false for auto-stacked ones). */
    default boolean draggable() {
        return true;
    }

    enum Column {
        LEFT,
        RIGHT
    }
}
