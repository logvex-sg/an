package com.logvex.crystal.module.render;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.StringSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.UpdateClock;
import com.logvex.crystal.world.BlockScanner;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/**
 * BlockEsp: highlights any block whose id contains a configurable fragment, so a player can
 * find an ore, a specific build block, or a rare structure material without x-ray.
 *
 * <p>The scan is a bounded, interval-cached cube around the player via the shared
 * {@link BlockScanner}; only loaded chunks are ever read and the match count is capped, so
 * the cost is predictable no matter how common the block is.
 */
public final class BlockEspModule extends WorldEspModule {

    private final StringSetting block = add("Block", "Block id fragment to highlight (e.g. 'diamond_ore').", "diamond_ore");
    private final IntSetting range = add("Range", "Cube radius to scan.", 24, 4, 64);
    private final IntSetting interval = add("ScanMs", "Minimum interval between scans.", 1000, 200, 5000);
    private final IntSetting maxBlocks = add("MaxBlocks", "Cap on blocks highlighted.", 128, 4, 512);
    private final BooleanSetting labels = add("Labels", "Label each match with its block id.", false);

    private final UpdateClock clock = new UpdateClock(1000);
    private List<BlockPos> cached = List.of();
    private String cachedQuery = "";

    public BlockEspModule() {
        super(Category.RENDER, "BlockEsp", "Highlights blocks matching a name fragment.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        String query = block.value() == null ? "" : block.value().toLowerCase().trim();
        clock.setIntervalMs(interval.value());
        if (query.isEmpty()) {
            cached = List.of();
            cachedQuery = query;
            return;
        }
        if (!query.equals(cachedQuery) || clock.ready()) {
            cachedQuery = query;
            cached = BlockScanner.find(client.world, client.player.getBlockPos(), range.value(),
                    range.value(), maxBlocks.value(),
                    state -> Registries.BLOCK.getId(state.getBlock()).getPath().contains(query));
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFF8FE3FF : palette.crystalSelf();
        int textColor = palette == null ? 0xFFFFFFFF : palette.text();
        for (BlockPos pos : cached) {
            out.addBox(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, color, 1.4f);
            if (labels.value()) {
                out.addLabel(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5,
                        Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).getPath(), textColor);
            }
        }
    }
}
