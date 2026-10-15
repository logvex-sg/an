package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * Auto-eat: uses a held or hotbar food item when hunger drops below a threshold, the way
 * a player would. It only triggers the normal use action; it never moves items between
 * slots or bypasses the eating animation.
 */
public final class AutoEatModule extends Module {

    private final IntSetting threshold = add("Threshold", "Eat when hunger is at or below this level.", 14, 1, 20);
    private final BooleanSetting eatInCombat = add("EatInCombat", "Also eat while a target is nearby.", true);
    private final BooleanSetting announce = add("Announce", "Notify when eating starts.", false);

    private long lastEatMillis;

    public AutoEatModule() {
        super(Category.PLAYER, "AutoEat", "Eats food automatically when hunger is low.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null) {
            return;
        }
        if (client.player.getHungerManager().getFoodLevel() > threshold.value()) {
            return;
        }
        if (!eatInCombat.value() && context().combatService().currentTarget() != null) {
            return;
        }
        if (System.currentTimeMillis() - lastEatMillis < 500) {
            return;
        }
        ItemStack held = client.player.getInventory().getSelectedStack();
        if (isFood(held) || !client.player.canConsume(false)) {
            // Already holding food, or the game says we cannot eat: use the held item normally.
            if (isFood(held)) {
                client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
                lastEatMillis = System.currentTimeMillis();
                if (announce.value()) {
                    context().notifications().info("Eating \u2661");
                }
            }
        }
    }

    private boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(net.minecraft.component.DataComponentTypes.FOOD);
    }
}
