package com.logvex.crystal.module.render;

import com.logvex.crystal.render.esp.BlockLabels;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.entity.BlockEntity;

/**
 * EnderChestEsp: outlines ender chests only, so a base's storage points stand out from the
 * ordinary chests around them.
 */
public final class EnderChestEspModule extends BlockEntityEspModule {

    public EnderChestEspModule() {
        super("EnderChestEsp", "Outlines ender chests.", 32, 96);
    }

    @Override
    protected boolean matches(BlockEntity entity) {
        return BlockLabels.isEnderChest(entity);
    }

    @Override
    protected int color(BlockEntity entity, Palette palette) {
        return palette == null ? 0xFFB39DFF : palette.tertiary();
    }

    @Override
    protected String label(BlockEntity entity) {
        return "ender";
    }
}
