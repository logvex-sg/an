package com.logvex.crystal.module.render;

import com.logvex.crystal.render.esp.BlockLabels;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.entity.BlockEntity;

/**
 * ChestEsp: outlines every storage container the client is tracking (chests, barrels,
 * hoppers, shulkers, ender chests and so on) with a type label.
 *
 * <p>A one-line specialisation of {@link BlockEntityEspModule}: the scanning, distance
 * limiting and capping are shared, so this module only states which entities it cares about
 * and how to colour them. Contents are never read.
 */
public final class ChestEspModule extends BlockEntityEspModule {

    private final BooleanSetting enderChests = add("EnderChests", "Include ender chests.", true);

    public ChestEspModule() {
        super("ChestEsp", "Outlines nearby storage containers with a type label.", 32, 96);
    }

    @Override
    protected boolean matches(BlockEntity entity) {
        String type = BlockLabels.container(entity);
        if (type == null) {
            return false;
        }
        return enderChests.value() || !"ender".equals(type);
    }

    @Override
    protected int color(BlockEntity entity, Palette palette) {
        return palette == null ? 0xFFFFC46B : palette.warning();
    }

    @Override
    protected String label(BlockEntity entity) {
        return BlockLabels.container(entity);
    }
}
