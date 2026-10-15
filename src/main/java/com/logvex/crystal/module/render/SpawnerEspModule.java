package com.logvex.crystal.module.render;

import com.logvex.crystal.render.esp.BlockLabels;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.theme.Palette;
import net.minecraft.block.entity.BlockEntity;

/**
 * SpawnerEsp: outlines mob spawners so an anarchy player can spot a grinder or a trapped
 * spawner before walking into it.
 *
 * <p>Also labels beacons, signs and lecterns when {@code Utilities} is on, since those are
 * the other block entities worth noticing from a distance.
 */
public final class SpawnerEspModule extends BlockEntityEspModule {

    private final BooleanSetting utilities = add("Utilities", "Also outline beacons, signs and lecterns.", false);

    public SpawnerEspModule() {
        super("SpawnerEsp", "Outlines spawners (and optionally beacons, signs and lecterns).", 32, 96);
    }

    @Override
    protected boolean matches(BlockEntity entity) {
        if (BlockLabels.utility(entity) == null) {
            return false;
        }
        return utilities.value() || "spawner".equals(BlockLabels.utility(entity));
    }

    @Override
    protected int color(BlockEntity entity, Palette palette) {
        return palette == null ? 0xFFFF7FB0 : palette.danger();
    }

    @Override
    protected String label(BlockEntity entity) {
        return BlockLabels.utility(entity);
    }
}
