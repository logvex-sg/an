package com.logvex.crystal.module.player;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Fast-place: lowers the client's item-use cooldown so blocks place as fast as the server
 * allows. This only reduces the local cooldown counter; the server still validates every
 * placement, so it cannot place blocks the player could not place normally.
 */
public final class FastPlaceModule extends Module {

    private final IntSetting cooldown = add("Cooldown", "Item-use cooldown in ticks (vanilla is 4).", 0, 0, 4);

    public FastPlaceModule() {
        super(Category.PLAYER, "FastPlace", "Reduces the local item-use cooldown.");
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        // Only shorten the cooldown while the player is actually holding a placeable item.
        var stack = client.player.getInventory().getSelectedStack();
        if (stack.isEmpty()) {
            return;
        }
        try {
            var accessor = (com.logvex.crystal.mixin.MinecraftClientAccessor) client;
            accessor.crystal$setItemUseCooldown(cooldown.value());
        } catch (Throwable t) {
            // Accessor missing (e.g. a mapping change): report once, then disable so the
            // module does not retry a broken cast every tick.
            if (reportErrorThrottled("could not set the item-use cooldown", t, 5000L)) {
                context().notifications().warn("FastPlace disabled: " + t.getClass().getSimpleName());
            }
            disable();
        }
    }
}
