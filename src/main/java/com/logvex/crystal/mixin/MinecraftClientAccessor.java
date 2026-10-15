package com.logvex.crystal.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the client's private item-use cooldown so {@code FastPlace} can shorten it.
 *
 * <p>Using an accessor mixin rather than a full {@code @Inject} keeps the change to a
 * single field and avoids reimplementing vanilla's item-use logic. The cooldown is a
 * client-side pacing value; the server still validates every placement.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("itemUseCooldown")
    void crystal$setItemUseCooldown(int cooldown);

    @Accessor("itemUseCooldown")
    int crystal$getItemUseCooldown();
}
