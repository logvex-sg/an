package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.world.WorldQuery;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Base for ESP modules that highlight loaded block entities (containers, spawners, beds and
 * so on).
 *
 * <p>Every such module needs the same three things: a distance limit, a per-frame cap and
 * an optional label. Those live here so a concrete module only declares its filter, colour
 * and label text. The scan walks the shared {@link WorldQuery} snapshot rather than
 * re-enumerating the world, so ten block-ESP modules cost about as much as one.
 */
public abstract class BlockEntityEspModule extends WorldEspModule {

    protected final IntSetting range;
    protected final BooleanSetting labels;
    protected final IntSetting maxBlocks;

    protected BlockEntityEspModule(String name, String description, int defaultRange, int maxRange) {
        super(Category.RENDER, name, description);
        this.range = add("Range", "Maximum distance to outline a block.", defaultRange, 4, maxRange);
        this.labels = add("Labels", "Show a type label.", true);
        this.maxBlocks = add("MaxBlocks", "Cap on blocks outlined per frame.", 128, 8, 512);
    }

    /** Whether this module highlights the given block entity. */
    protected abstract boolean matches(BlockEntity entity);

    /** The outline colour for the entity. */
    protected abstract int color(BlockEntity entity, Palette palette);

    /** The label text for the entity, or null for none. */
    protected abstract String label(BlockEntity entity);

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int dimColor = palette == null ? 0xFFFFFFFF : palette.text();
        double maxDistance = range.value();
        double maxSquared = maxDistance * maxDistance;
        int cap = maxBlocks.value();
        var pos = client.player.getEntityPos();
        int drawn = 0;
        for (BlockEntity entity : WorldQuery.get().blockEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!matches(entity)) {
                continue;
            }
            BlockPos p = entity.getPos();
            double dx = p.getX() + 0.5 - pos.x;
            double dy = p.getY() + 0.5 - pos.y;
            double dz = p.getZ() + 0.5 - pos.z;
            if (dx * dx + dy * dy + dz * dz > maxSquared) {
                continue;
            }
            int color = color(entity, palette);
            out.addBox(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, color, 1.5f);
            drawn++;
            if (labels.value()) {
                String text = label(entity);
                if (text != null) {
                    out.addLabel(p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5, text, dimColor);
                }
            }
        }
    }
}
