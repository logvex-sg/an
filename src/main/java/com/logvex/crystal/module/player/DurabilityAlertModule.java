package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.util.UpdateClock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * DurabilityAlert: warns when a worn or held item is nearly broken, so a pickaxe or a set
 * of armour does not silently die mid-fight.
 *
 * <p>Reads only the item stacks the client already has. A warning fires once per item when
 * it first crosses the threshold, then re-arms only after the item is repaired or swapped
 * out, so it cannot spam the chat every tick. This is informational: it never moves items.
 */
public final class DurabilityAlertModule extends Module {

    private final IntSetting threshold = add("Threshold", "Warn when durability drops below this percent.", 15, 1, 90);
    private final BooleanSetting armor = add("Armor", "Watch worn armour.", true);
    private final BooleanSetting held = add("Held", "Watch the held item.", true);
    private final BooleanSetting offhand = add("Offhand", "Watch the offhand item.", true);
    private final BooleanSetting actionBar = add("ActionBar", "Show the warning on the action bar too.", false);

    private final UpdateClock clock = new UpdateClock(500);
    /** Items already warned about, keyed by slot+item name, cleared when the item changes. */
    private final java.util.Map<String, Boolean> warned = new java.util.HashMap<>();

    public DurabilityAlertModule() {
        super(Category.PLAYER, "DurabilityAlert", "Warns when an item is nearly broken.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !clock.ready()) {
            return;
        }
        int warnBelow = threshold.value();
        if (armor.value()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                check(client, slot.name(), client.player.getEquippedStack(slot), warnBelow);
            }
        }
        if (held.value()) {
            check(client, "held", client.player.getMainHandStack(), warnBelow);
        }
        if (offhand.value()) {
            check(client, "offhand", client.player.getOffHandStack(), warnBelow);
        }
    }

    private void check(MinecraftClient client, String slot, ItemStack stack, int warnBelow) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable()) {
            warned.remove(slot);
            return;
        }
        int max = stack.getMaxDamage();
        int remaining = max - stack.getDamage();
        double percent = max <= 0 ? 100 : remaining * 100.0 / max;
        String key = slot + "|" + stack.getItem().toString();
        if (percent <= warnBelow) {
            if (warned.putIfAbsent(key, Boolean.TRUE) == null) {
                String name = stack.getName().getString();
                String message = "\u26A0 " + name + " at " + Math.round(percent) + "% (" + remaining + " left)";
                context().notifications().warn(message);
                if (actionBar.value()) {
                    context().notifications().actionBar(message,
                            com.logvex.crystal.notification.NotificationLevel.WARN);
                }
            }
        } else {
            // Re-arm once the item is healthy again (repaired, or a fresh item in the slot).
            warned.remove(key);
        }
    }

    @Override
    protected void onDisable() {
        warned.clear();
    }
}
