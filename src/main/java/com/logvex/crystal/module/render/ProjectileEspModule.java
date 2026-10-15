package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.EspFilter;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;

/**
 * ProjectileEsp: outlines arrows, pearls, snowballs, potions and other projectiles in
 * flight, with a separate colour for ender pearls so a pearl-in-flight is obvious.
 *
 * <p>Only entities the client has been told about are considered. This is a visual
 * overlay only.
 */
public final class ProjectileEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to outline a projectile.", 64, 8, 160);
    private final BooleanSetting pearls = add("Pearls", "Highlight ender pearls distinctly.", true);
    private final BooleanSetting names = add("Names", "Show the projectile type as a label.", false);
    private final BooleanSetting arrowsOnly = add("ArrowsOnly", "Only outline arrows.", false);
    private final IntSetting maxProjectiles = add("MaxProjectiles", "Cap outlined per frame.", 48, 4, 192);

    public ProjectileEspModule() {
        super(Category.RENDER, "ProjectileEsp", "Outlines projectiles in flight.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int normal = palette == null ? 0xFFD4B0F0 : palette.tertiary();
        int pearlColor = palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        double maxDistance = range.value();
        int cap = maxProjectiles.value();
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!isProjectile(entity)) {
                continue;
            }
            if (arrowsOnly.value() && !(entity instanceof PersistentProjectileEntity)) {
                continue;
            }
            if (EspFilter.skip(entity, client.player, maxDistance)) {
                continue;
            }
            var box = entity.getBoundingBox().expand(0.15);
            boolean pearl = entity instanceof EnderPearlEntity;
            int color = pearls.value() && pearl ? pearlColor : normal;
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 1.3f);
            drawn++;
            if (names.value()) {
                out.addLabel((box.minX + box.maxX) / 2, box.maxY + 0.3, (box.minZ + box.maxZ) / 2,
                        entity.getType().getName().getString(), color);
            }
        }
    }

    private static boolean isProjectile(Entity entity) {
        return entity instanceof ProjectileEntity
                || entity instanceof ThrownEntity
                || entity instanceof PersistentProjectileEntity
                || entity instanceof PotionEntity;
    }
}
