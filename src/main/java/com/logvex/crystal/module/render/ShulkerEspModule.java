package com.logvex.crystal.module.render;

import com.logvex.crystal.render.esp.BlockLabels;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.entity.BlockEntity;

/**
 * ShulkerEsp: outlines shulker boxes, which are the portable storage anarchy players move
 * loot in. Kept separate from ChestEsp so its colour and label can differ.
 */
public final class ShulkerEspModule extends BlockEntityEspModule {

    public ShulkerEspModule() {
        super("ShulkerEsp", "Outlines shulker boxes.", 32, 96);
    }

    @Override
    protected boolean matches(BlockEntity entity) {
        return BlockLabels.isShulker(entity);
    }

    @Override
    protected int color(BlockEntity entity, Palette palette) {
        return palette == null ? 0xFFFF9EC4 : palette.primary();
    }

    @Override
    protected String label(BlockEntity entity) {
        return "shulker";
    }
}
