package com.logvex.crystal.module.combat;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.DoubleSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Indicates whether the player is currently in a critical-hit position (falling) so they
 * know when to swing. Reads the player's own state only; it never attacks.
 */
public final class CritAssistModule extends Module {

    private final DoubleSetting minFall = add("MinFall", "Minimum downward velocity to count as a crit.",
            0.08, 0.0, 0.5);
    private final BooleanSetting sound = add("Sound", "Play a soft chime when a crit window opens.", false);

    private boolean critReady;
    private boolean sounded;

    public CritAssistModule() {
        super(Category.COMBAT, "CritAssist", "Shows when you are falling for a critical hit.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        boolean falling = !client.player.isOnGround() && client.player.getVelocity().y < -minFall.value();
        critReady = falling;
        if (critReady && !sounded && sound.value()) {
            com.logvex.crystal.cosmetic.GuiSounds.click();
            sounded = true;
        } else if (!critReady) {
            sounded = false;
        }
    }

    public boolean critReady() {
        return critReady;
    }
}
