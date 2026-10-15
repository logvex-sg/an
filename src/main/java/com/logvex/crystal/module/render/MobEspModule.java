package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.EspFilter;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.Format;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * MobEsp: outlines hostile mobs (and optionally all mobs) with a health label, so a fight
 * is not interrupted by an unnoticed creeper.
 *
 * <p>Reads only loaded entities; distance-limited and capped per frame.
 */
public final class MobEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to outline a mob.", 48, 8, 128);
    private final BooleanSetting hostileOnly = add("HostileOnly", "Only outline hostile mobs.", true);
    private final BooleanSetting health = add("Health", "Show a health label.", true);
    private final BooleanSetting names = add("Names", "Show the mob type as a label.", false);
    private final BooleanSetting distance = add("Distance", "Show a distance label.", false);
    private final IntSetting maxMobs = add("MaxMobs", "Cap on mobs outlined per frame.", 48, 4, 256);

    public MobEspModule() {
        super(Category.RENDER, "MobEsp", "Outlines nearby mobs with a health label.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int hostile = palette == null ? 0xFFFF7FB0 : palette.danger();
        int passive = palette == null ? 0xFFB7E3B0 : palette.tertiary();
        int healthColor = palette == null ? 0xFFFF9EC4 : palette.health();
        int dimColor = palette == null ? 0xFFB0B0C0 : palette.textDim();
        double maxDistance = range.value();
        int cap = maxMobs.value();
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!(entity instanceof MobEntity mob) || entity instanceof PlayerEntity) {
                continue;
            }
            if (hostileOnly.value() && !(mob instanceof HostileEntity)) {
                continue;
            }
            if (EspFilter.skip(mob, client.player, maxDistance)) {
                continue;
            }
            var box = mob.getBoundingBox();
            int color = mob instanceof HostileEntity ? hostile : passive;
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 1.4f);
            drawn++;
            double midX = (box.minX + box.maxX) / 2;
            double midZ = (box.minZ + box.maxZ) / 2;
            double labelY = box.maxY + 0.4;
            if (names.value()) {
                out.addLabel(midX, labelY, midZ, mob.getName().getString(), color);
                labelY += 0.42;
            }
            if (health.value()) {
                out.addLabel(midX, labelY, midZ, String.format("%.1f\u2661", mob.getHealth()), healthColor);
            }
            if (distance.value()) {
                out.addLabel(midX, box.minY - 0.4, midZ,
                        Format.distance(client.player.distanceTo(mob)), dimColor);
            }
        }
    }
}
