package com.logvex.crystal.combat;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Reads the damage-reduction facts the client can legitimately observe on another entity:
 * the Resistance effect and the armour enchantment protection points (EPF).
 *
 * <p>Everything here is read from data the server has already synced to the client (status
 * effects and equipped stacks), so nothing hidden is inferred. The enchantment values use the
 * same per-level formula the game applies for an explosion: Blast Protection contributes
 * {@code level * 4} EPF and general Protection contributes {@code level} EPF; the damage model
 * sums them and caps the total at 20.
 *
 * <p>The pure {@link #protectionEpf} method is separated out so the arithmetic is unit-testable
 * without a live entity.
 */
public final class TargetProtection {

    /** The armour slots whose protection enchantments apply to an explosion. */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private TargetProtection() {
    }

    /**
     * The enchantment protection points for the given levels. Blast Protection contributes
     * 4 EPF per level (it is the relevant one for a crystal blast) and general Protection
     * contributes 1 EPF per level. The result is not capped here; the damage model caps it.
     */
    public static double protectionEpf(int protectionLevel, int blastProtectionLevel) {
        double protection = Math.max(0, protectionLevel);
        double blast = Math.max(0, blastProtectionLevel);
        return protection + blast * 4.0;
    }

    /**
     * Sums the protection EPF across an entity's four armour slots. Best-effort: a stack whose
     * enchantment component is unavailable contributes nothing rather than failing the read.
     */
    public static double armorProtectionEpf(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        double total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            total += stackProtectionEpf(stack.getEnchantments());
        }
        return total;
    }

    /** Protection EPF for one enchantment component, summing Protection and Blast Protection. */
    public static double stackProtectionEpf(ItemEnchantmentsComponent enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return 0;
        }
        int protection = 0;
        int blast = 0;
        for (var entry : enchantments.getEnchantmentEntries()) {
            int level = entry.getIntValue();
            if (level <= 0) {
                continue;
            }
            if (matches(entry.getKey(), Enchantments.PROTECTION)) {
                protection += level;
            } else if (matches(entry.getKey(), Enchantments.BLAST_PROTECTION)) {
                blast += level;
            }
        }
        return protectionEpf(protection, blast);
    }

    private static boolean matches(RegistryEntry<Enchantment> entry, RegistryKey<Enchantment> key) {
        try {
            return entry.matchesKey(key);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** The Resistance effect amplifier on {@code entity}, or -1 when it has none. */
    public static int resistanceAmplifier(LivingEntity entity) {
        if (entity == null) {
            return -1;
        }
        StatusEffectInstance effect = entity.getStatusEffect(StatusEffects.RESISTANCE);
        return effect == null ? -1 : effect.getAmplifier();
    }
}
