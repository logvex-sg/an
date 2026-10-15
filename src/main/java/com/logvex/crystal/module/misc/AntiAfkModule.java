package com.logvex.crystal.module.misc;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * AntiAfk: performs a small, local movement (a turn or a hop) on an interval so the player
 * is not flagged as idle. Everything happens client-side through normal input; no packets
 * are forged.
 */
public final class AntiAfkModule extends Module {

    private final IntSetting interval = add("IntervalSeconds", "Seconds between actions.", 20, 5, 120);
    private final IntSetting turnAmount = add("TurnDegrees", "How far to turn each action.", 20, 5, 90);
    private final com.logvex.crystal.setting.BooleanSetting jump = add("Jump", "Also jump occasionally.", false);

    private long lastAction;
    private boolean alternate;

    public AntiAfkModule() {
        super(Category.MISC, "AntiAfk", "Small periodic actions to avoid idle kicks.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        if (System.currentTimeMillis() - lastAction < interval.value() * 1000L) {
            return;
        }
        lastAction = System.currentTimeMillis();
        alternate = !alternate;
        float yaw = client.player.getYaw() + (alternate ? turnAmount.value() : -turnAmount.value());
        client.player.setYaw(yaw);
        if (jump.value() && client.player.isOnGround()) {
            client.player.jump();
        }
    }

    @Override
    protected void onDisable() {
        lastAction = 0;
    }

    /** Exposed for tests: the yaw offset the next action would apply. */
    public float nextTurn() {
        return alternate ? turnAmount.value() : -turnAmount.value();
    }
}
