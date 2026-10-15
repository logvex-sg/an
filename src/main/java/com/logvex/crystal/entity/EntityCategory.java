package com.logvex.crystal.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.VehicleEntity;

/**
 * Coarse classification of a live entity into the categories the ESP filters and the
 * entity-information HUD talk about.
 *
 * <p>The mapping is derived only from the entity's own type - never from hidden server
 * state - so it is safe to expose in a purely visual client.
 */
public enum EntityCategory {

    /** Another player. */
    PLAYER("Player"),
    /** A hostile mob (zombie, skeleton, creeper, ...). */
    HOSTILE("Hostile"),
    /** A passive or neutral mob (animal, villager, iron golem, ...). */
    PASSIVE("Passive"),
    /** A dropped item stack. */
    ITEM("Item"),
    /** An arrow, pearl, potion or other projectile in flight. */
    PROJECTILE("Projectile"),
    /** A boat, minecart or other vehicle. */
    VEHICLE("Vehicle"),
    /** An experience orb. */
    EXPERIENCE("XP"),
    /** An armour stand or other decoration. */
    DECORATION("Decoration"),
    /** Anything not otherwise classified. */
    OTHER("Other");

    private final String label;

    EntityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Classifies {@code entity}. The local player is still a {@link #PLAYER}; callers that
     * want to exclude it check the reference themselves (the filter does).
     */
    public static EntityCategory of(Entity entity) {
        if (entity == null) {
            return OTHER;
        }
        if (entity instanceof PlayerEntity) {
            return PLAYER;
        }
        if (entity instanceof ItemEntity) {
            return ITEM;
        }
        if (entity instanceof ProjectileEntity) {
            return PROJECTILE;
        }
        if (entity instanceof ExperienceOrbEntity) {
            return EXPERIENCE;
        }
        if (entity instanceof ArmorStandEntity) {
            return DECORATION;
        }
        if (entity instanceof HostileEntity) {
            return HOSTILE;
        }
        if (entity instanceof PassiveEntity) {
            return PASSIVE;
        }
        if (entity instanceof MobEntity) {
            // A mob that is neither hostile nor passive (e.g. a modded or neutral mob).
            return PASSIVE;
        }
        if (entity instanceof VehicleEntity) {
            return VEHICLE;
        }
        return OTHER;
    }

    /** True when the entity is a living entity with health/armour worth labelling. */
    public static boolean isLiving(Entity entity) {
        return entity instanceof LivingEntity;
    }
}
