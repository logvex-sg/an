package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.combat.CrystalDamage;
import com.logvex.crystal.combat.DamageResult;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * CrystalEsp: highlights end crystals, optionally with the predicted damage the crystal
 * would deal to the local player and to the current target.
 *
 * <p>The damage figure is the same {@link CrystalDamage} model the HUD uses, so the numbers
 * agree everywhere. Crystals are read straight from the loaded entity list; nothing hidden
 * is inferred.
 */
public final class CrystalEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Maximum distance to highlight a crystal.", 32, 4, 96);
    private final BooleanSetting selfDamage = add("SelfDamage", "Show predicted damage to you.", true);
    private final BooleanSetting targetDamage = add("TargetDamage", "Show predicted damage to your target.", true);
    private final IntSetting maxCrystals = add("MaxCrystals", "Cap on crystals highlighted per frame.", 96, 8, 512);

    public CrystalEspModule() {
        super(Category.RENDER, "CrystalEsp", "Highlights end crystals with damage estimates.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int crystalColor = palette == null ? 0xFFFF7FB0 : palette.crystalDamage();
        int selfColor = palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        double maxDistance = range.value();
        int cap = maxCrystals.value();
        int drawn = 0;
        for (Entity entity : client.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!(entity instanceof EndCrystalEntity crystal) || crystal.isRemoved()) {
                continue;
            }
            double dist = client.player.distanceTo(crystal);
            if (dist > maxDistance) {
                continue;
            }
            Box box = crystal.getBoundingBox().expand(0.1);
            out.addBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, crystalColor, 2.0f);
            drawn++;
            Vec3d pos = crystal.getEntityPos();
            if (selfDamage.value()) {
                DamageResult self = CrystalDamage.evaluate(
                        client.player.getEntityPos().distanceTo(pos), client.player.getArmor(), 2.0,
                        client.player.getHealth(), CrystalDamage.exposureFor(client.player.canSee(crystal)));
                out.addLabel(pos.x, box.maxY + 0.4, pos.z, String.format("\u2665%.1f", self.applied()), selfColor);
            }
            if (targetDamage.value() && bootstrap != null) {
                var target = bootstrap.combat().currentTarget();
                if (target != null) {
                    DamageResult toTarget = CrystalDamage.evaluate(
                            target.entity().getEntityPos().distanceTo(pos), target.armor(), 2.0,
                            target.health(), CrystalDamage.exposureFor(target.hasLineOfSight()));
                    out.addLabel(pos.x, box.minY - 0.4, pos.z, String.format("\u2726%.1f", toTarget.applied()), crystalColor);
                }
            }
        }
    }
}
