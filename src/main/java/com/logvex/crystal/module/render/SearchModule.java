package com.logvex.crystal.module.render;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.StringSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/**
 * Search: scans nearby blocks for a name fragment and reports the closest matches, so the
 * player can find an ore, a spawner or a specific build material without x-ray.
 *
 * <p>The scan is throttled (once every {@code interval} ticks) and capped in radius, so it
 * never costs meaningful frame time. It reads already-loaded block states only.
 */
public final class SearchModule extends Module {

    private final StringSetting query = add("Query", "Block name fragment to look for (e.g. diamond).", "diamond");
    private final IntSetting radius = add("Radius", "Scan radius in blocks.", 24, 4, 48);
    private final IntSetting interval = add("IntervalTicks", "Ticks between scans.", 20, 5, 100);
    private final IntSetting maxResults = add("MaxResults", "How many matches to report.", 5, 1, 20);

    private int tickCounter;
    private List<Match> matches = List.of();

    public SearchModule() {
        super(Category.RENDER, "Search", "Finds nearby blocks matching a name fragment.");
    }

    /** A found block and its distance from the player. */
    public record Match(BlockPos pos, String name, double distance) {
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (++tickCounter < interval.value()) {
            return;
        }
        tickCounter = 0;
        String needle = query.value().toLowerCase();
        if (needle.isBlank()) {
            matches = List.of();
            return;
        }
        int r = radius.value();
        BlockPos origin = client.player.getBlockPos();
        List<Match> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                        continue;
                    }
                    Block block = client.world.getBlockState(pos).getBlock();
                    String id = Registries.BLOCK.getId(block).getPath();
                    if (id.contains(needle)) {
                        double distance = Math.sqrt(client.player.squaredDistanceTo(
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                        found.add(new Match(pos, id, distance));
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        matches = found.size() > maxResults.value() ? found.subList(0, maxResults.value()) : found;
        if (!matches.isEmpty()) {
            Match nearest = matches.get(0);
            context().notifications().info("Found " + matches.size() + " x " + nearest.name()
                    + " at " + nearest.pos().toShortString() + " \u2661");
        }
    }

    @Override
    protected void onDisable() {
        matches = List.of();
        tickCounter = 0;
    }

    public List<Match> matches() {
        return matches;
    }
}
