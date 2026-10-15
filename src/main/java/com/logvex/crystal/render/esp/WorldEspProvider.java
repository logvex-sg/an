package com.logvex.crystal.render.esp;

import net.minecraft.client.MinecraftClient;

/**
 * Implemented by any module that contributes world-space ESP. The renderer calls every
 * registered provider once per frame to fill the {@link WorldEspCollector}; the provider
 * decides what to show and appends boxes/labels, but never draws.
 *
 * <p>Keeping collection separate from drawing means the provider can be exercised (and its
 * decisions tested) without a live GL pipeline, and one provider throwing cannot corrupt
 * the render pass.
 */
public interface WorldEspProvider {

    /** Appends this provider's ESP for the current frame. Must not draw or mutate state. */
    void collectEsp(WorldEspCollector out, MinecraftClient client);
}
