package com.logvex.crystal.render.esp;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;

/**
 * Maps a block entity to the short label the ESP modules draw above it, so every module
 * names a chest "chest" and not three different spellings of it.
 *
 * <p>Uses {@code instanceof} rather than raw {@link BlockEntityType} constants where the
 * concrete class is stable, which is robust across the small mapping changes that happen
 * between Minecraft versions.
 */
public final class BlockLabels {

    private BlockLabels() {
    }

    /**
     * A short, lower-case label for a storage or interactive block entity, or null when the
     * entity is not one this client labels.
     */
    public static String container(BlockEntity entity) {
        if (entity instanceof TrappedChestBlockEntity) {
            return "trapped";
        }
        if (entity instanceof ChestBlockEntity || entity.getType() == BlockEntityType.CHEST) {
            return "chest";
        }
        if (entity instanceof BarrelBlockEntity || entity.getType() == BlockEntityType.BARREL) {
            return "barrel";
        }
        if (entity instanceof ShulkerBoxBlockEntity || entity.getType() == BlockEntityType.SHULKER_BOX) {
            return "shulker";
        }
        if (entity instanceof EnderChestBlockEntity || entity.getType() == BlockEntityType.ENDER_CHEST) {
            return "ender";
        }
        if (entity instanceof HopperBlockEntity || entity.getType() == BlockEntityType.HOPPER) {
            return "hopper";
        }
        if (entity instanceof BrewingStandBlockEntity || entity.getType() == BlockEntityType.BREWING_STAND) {
            return "brewing";
        }
        if (entity instanceof DispenserBlockEntity || entity.getType() == BlockEntityType.DISPENSER) {
            return "dispenser";
        }
        if (entity instanceof DropperBlockEntity || entity.getType() == BlockEntityType.DROPPER) {
            return "dropper";
        }
        if (entity instanceof FurnaceBlockEntity) {
            return "furnace";
        }
        return null;
    }

    /** A short label for a utility block entity (spawner, beacon, sign, ...), or null. */
    public static String utility(BlockEntity entity) {
        if (entity instanceof MobSpawnerBlockEntity) {
            return "spawner";
        }
        if (entity instanceof BeaconBlockEntity) {
            return "beacon";
        }
        if (entity instanceof SignBlockEntity) {
            return "sign";
        }
        if (entity instanceof LecternBlockEntity) {
            return "lectern";
        }
        if (entity instanceof CampfireBlockEntity) {
            return "campfire";
        }
        return null;
    }

    /** True when the block entity is a shulker box. */
    public static boolean isShulker(BlockEntity entity) {
        return entity instanceof ShulkerBoxBlockEntity
                || entity.getType() == BlockEntityType.SHULKER_BOX;
    }

    /** True when the block entity is an ender chest. */
    public static boolean isEnderChest(BlockEntity entity) {
        return entity instanceof EnderChestBlockEntity
                || entity.getType() == BlockEntityType.ENDER_CHEST;
    }
}
