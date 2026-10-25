package com.logvex.crystal.cosmetic.render;

import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * The model-layer ids for the cosmetic player accessories.
 *
 * <p>One layer is registered with the Fabric {@code EntityModelLayerRegistry} so the
 * accessories are baked alongside the vanilla entity models; the renderer fetches the
 * baked part by this id. Names are original to LOGVEX CRYSTAL.
 */
public final class CosmeticModelLayers {

    /** The player-accessories layer (cat ears, bow, halo). */
    public static final EntityModelLayer PLAYER_ACCESSORIES =
            new EntityModelLayer(Identifier.of("logvex-crystal", "player_accessories"), "main");

    private CosmeticModelLayers() {
    }
}
