package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.UpdateClock;
import com.logvex.crystal.world.BlockScanner;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * PortalEsp: highlights nether portal blocks, so a portal back to base is easy to find.
 *
 * <p>Portal frames are blocks, so this uses the bounded {@link BlockScanner} with an
 * interval cache; only loaded chunks are ever read.
 */
public final class PortalEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Cube radius to scan for portals.", 32, 8, 64);
    private final BooleanSetting labels = add("Labels", "Label each portal block.", false);
    private final IntSetting interval = add("ScanMs", "Minimum interval between scans.", 750, 100, 2000);
    private final IntSetting maxBlocks = add("MaxBlocks", "Cap on portal blocks highlighted.", 256, 8, 1024);

    private final UpdateClock clock = new UpdateClock(750);
    private List<BlockPos> cached = List.of();

    public PortalEspModule() {
        super(Category.RENDER, "PortalEsp", "Highlights nether portal blocks.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        clock.setIntervalMs(interval.value());
        if (clock.ready()) {
            cached = BlockScanner.find(client.world, client.player.getBlockPos(), range.value(),
                    range.value(), maxBlocks.value(),
                    state -> state.getBlock() instanceof NetherPortalBlock || isPortalBlock(state.getBlock()));
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFFB39DFF : palette.tertiary();
        int textColor = palette == null ? 0xFFFFFFFF : palette.text();
        for (BlockPos pos : cached) {
            out.addBox(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, color, 1.4f);
            if (labels.value()) {
                out.addLabel(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, "portal", textColor);
            }
        }
    }

    /** True for any block implementing the vanilla portal marker interface. */
    private static boolean isPortalBlock(Block block) {
        return block instanceof net.minecraft.block.Portal;
    }
}
