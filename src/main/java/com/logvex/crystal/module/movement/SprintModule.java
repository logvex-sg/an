package com.logvex.crystal.module.movement;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Keeps the player sprinting whenever they are moving forward on the ground, which vanilla
 * already allows via the sprint key but many players forget to hold. Purely client-side
 * input: it toggles the sprint state the game would set itself.
 */
public final class SprintModule extends Module {

    private final BooleanSetting onlyOnGround = add("OnlyOnGround", "Only force sprint while on the ground.", false);

    public SprintModule() {
        super(Category.MOVEMENT, "Sprint", "Automatically sprint while moving.");
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
        // Respect the player's own hunger/state: only sprint when the game would allow it.
        if (!client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6
                && client.player.input.hasForwardMovement()) {
            client.player.setSprinting(true);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.setSprinting(false);
        }
    }
}
