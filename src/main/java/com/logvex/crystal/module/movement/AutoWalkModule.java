package com.logvex.crystal.module.movement;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Auto-walk: holds forward movement in the current facing direction. Client-side input
 * only, and disabled on disconnect. Useful for long anarchy travel; it does not path-find
 * or interact with anything.
 */
public final class AutoWalkModule extends Module {

    private final DoubleSetting speed = add("Speed", "Movement speed multiplier applied to velocity.", 1.0, 0.5, 1.5);
    private final IntSetting pauseOnDamage = add("PauseOnDamageMs", "Stop walking for this long after taking damage.",
            800, 0, 3000);

    private long pausedUntil;

    public AutoWalkModule() {
        super(Category.MOVEMENT, "AutoWalk", "Walks forward continuously in the facing direction.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        if (System.currentTimeMillis() < pausedUntil) {
            return;
        }
        if (client.player.hurtTime > 0 && pauseOnDamage.value() > 0) {
            pausedUntil = System.currentTimeMillis() + pauseOnDamage.value();
            return;
        }
        // Only nudge velocity; never override the player's own input.
        Vec3d look = client.player.getRotationVecClient();
        Vec3d flat = new Vec3d(look.x, 0, look.z).normalize();
        double factor = 0.1 * speed.value();
        client.player.addVelocity(flat.x * factor, 0, flat.z * factor);
    }

    @Override
    protected void onDisable() {
        pausedUntil = 0;
    }

    public boolean paused() {
        return System.currentTimeMillis() < pausedUntil;
    }
}
