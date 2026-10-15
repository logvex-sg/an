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
import net.minecraft.block.BedBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * BedEsp: highlights beds, which matter in the End (for crystals) and in the overworld (for
 * a quick respawn point).
 *
 * <p>Beds are blocks, not block entities, so this scans a bounded cube around the player
 * using the shared {@link BlockScanner}. Results are cached on an interval rather than
 * re-scanned every frame, and the scan never leaves loaded chunks.
 */
public final class BedEspModule extends WorldEspModule {

    private final IntSetting range = add("Range", "Cube radius to scan for beds.", 32, 8, 64);
    private final BooleanSetting labels = add("Labels", "Label the bed colour.", false);
    private final IntSetting interval = add("ScanMs", "Minimum interval between scans.", 500, 100, 2000);
    private final IntSetting maxBeds = add("MaxBeds", "Cap on beds highlighted.", 128, 8, 512);

    private final UpdateClock clock = new UpdateClock(500);
    private List<BlockPos> cached = List.of();

    public BedEspModule() {
        super(Category.RENDER, "BedEsp", "Highlights nearby beds.");
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        clock.setIntervalMs(interval.value());
        if (clock.ready()) {
            cached = BlockScanner.find(client.world, client.player.getBlockPos(), range.value(),
                    range.value(), maxBeds.value(), state -> state.getBlock() instanceof BedBlock);
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFFFF9EC4 : palette.primary();
        int textColor = palette == null ? 0xFFFFFFFF : palette.text();
        for (BlockPos pos : cached) {
            out.addBox(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 0.6, pos.getZ() + 1, color, 1.4f);
            if (labels.value()) {
                out.addLabel(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, "bed", textColor);
            }
        }
    }
}
