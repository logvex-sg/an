package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * StorageEsp: outlines nearby storage containers (chests, barrels, shulkers, hoppers,
 * furnaces, dispensers, brewing stands and ender chests) using the block entities the
 * client already tracks.
 *
 * <p>Only loaded block entities are considered, and the scan is limited to a cube around
 * the player, so it is cheap and never claims to see into an unloaded chunk. The container
 * contents are never read — this is a location highlight, not a looting tool.
 */
public final class StorageEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Cube radius to scan for containers.", 24, 4, 64);
    private final BooleanSetting labels = add("Labels", "Show the container type as a label.", true);
    private final BooleanSetting enderChests = add("EnderChests", "Include ender chests.", true);
    private final IntSetting maxContainers = add("MaxContainers", "Cap on containers outlined per frame.", 128, 8, 512);

    public StorageEspModule() {
        super(Category.RENDER, "StorageEsp", "Outlines nearby storage containers.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFFFFC46B : palette.warning();
        int labelColor = palette == null ? 0xFFFFFFFF : palette.text();
        int radius = range.value();
        int cap = maxContainers.value();
        BlockPos origin = client.player.getBlockPos();
        int drawn = 0;
        for (int dx = -radius; dx <= radius && drawn < cap; dx++) {
            for (int dy = -radius; dy <= radius && drawn < cap; dy++) {
                for (int dz = -radius; dz <= radius && drawn < cap; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    BlockEntity entity = client.world.getBlockEntity(pos);
                    if (entity == null) {
                        continue;
                    }
                    String type = classify(entity.getType(), entity);
                    if (type == null) {
                        continue;
                    }
                    out.addBox(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, color, 1.5f);
                    drawn++;
                    if (labels.value()) {
                        out.addLabel(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, type, labelColor);
                    }
                }
            }
        }
    }

    /** Returns a short type label for a storage block entity, or null if it is not one. */
    private String classify(BlockEntityType<?> type, BlockEntity entity) {
        if (type == BlockEntityType.CHEST || entity instanceof ChestBlockEntity) {
            return "chest";
        }
        if (type == BlockEntityType.BARREL || entity instanceof BarrelBlockEntity) {
            return "barrel";
        }
        if (type == BlockEntityType.SHULKER_BOX || entity instanceof ShulkerBoxBlockEntity) {
            return "shulker";
        }
        if (type == BlockEntityType.HOPPER || entity instanceof HopperBlockEntity) {
            return "hopper";
        }
        if (type == BlockEntityType.BREWING_STAND || entity instanceof BrewingStandBlockEntity) {
            return "brewing";
        }
        if (type == BlockEntityType.DISPENSER || entity instanceof DispenserBlockEntity) {
            return "dispenser";
        }
        if (type == BlockEntityType.DROPPER || entity instanceof DropperBlockEntity) {
            return "dropper";
        }
        if (entity instanceof AbstractFurnaceBlockEntity) {
            return "furnace";
        }
        if (enderChests.value() && (type == BlockEntityType.ENDER_CHEST || entity instanceof EnderChestBlockEntity)) {
            return "ender";
        }
        return null;
    }
}
