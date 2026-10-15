package com.logvex.crystal.render.esp;

import com.logvex.crystal.client.CrystalClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Shared decisions every entity ESP makes the same way, so the "is this entity worth
 * drawing, and in what colour" logic lives in one place rather than being re-implemented
 * (and drifting) across PlayerEsp, MobEsp, ItemEsp and the rest.
 *
 * <p>Deliberately Minecraft-light: it only reads entity state the client already has.
 */
public final class EspFilter {

    /** Friend colour when the friend list is unavailable (before bootstrap). */
    private static final int FRIEND_FALLBACK = 0xFF8FE3FF;
    /** Enemy/neutral player colour fallback. */
    private static final int PLAYER_FALLBACK = 0xFFFF7FB0;

    private EspFilter() {
    }

    /**
     * Whether an entity should be skipped by an entity ESP. Skips the local player, removed
     * and invisible entities, and anything beyond {@code maxDistance}.
     */
    public static boolean skip(Entity entity, PlayerEntity local, double maxDistance) {
        if (entity == null || entity == local || entity.isRemoved() || entity.isInvisible()) {
            return true;
        }
        return local != null && local.distanceTo(entity) > maxDistance;
    }

    /** True when the entity is a friend according to the client's friend list. */
    public static boolean isFriend(Entity entity) {
        CrystalClient bootstrap = CrystalClient.get();
        return bootstrap != null && entity instanceof PlayerEntity
                && bootstrap.friends().isFriend(entity.getName().getString());
    }

    /** The colour to draw an entity with, honouring friend status. */
    public static int colorFor(Entity entity, int normalColor, int friendColor) {
        return isFriend(entity) ? friendColor : normalColor;
    }

    /** A friend-aware colour using the client's default palette fallbacks. */
    public static int defaultColorFor(Entity entity, int normalColor) {
        return isFriend(entity) ? FRIEND_FALLBACK : normalColor;
    }
}
