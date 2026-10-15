package com.logvex.crystal.module.movement;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Fast-stop: removes leftover horizontal velocity when the player stops pressing a
 * movement key, so they do not slide. Client-side prediction only.
 */
public final class FastStopModule extends Module {

    private final BooleanSetting onlyOnGround = add("OnlyOnGround", "Only stop quickly while grounded.", true);

    public FastStopModule() {
        super(Category.MOVEMENT, "FastStop", "Stops movement immediately when keys are released.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        if (onlyOnGround.value() && !client.player.isOnGround()) {
            return;
        }
        if (!client.player.input.hasForwardMovement()) {
            var velocity = client.player.getVelocity();
            if (Math.abs(velocity.x) > 0.001 || Math.abs(velocity.z) > 0.001) {
                client.player.setVelocity(velocity.x * 0.2, velocity.y, velocity.z * 0.2);
            }
        }
    }
}
