package com.logvex.crystal.module.misc;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.scan.ChestLoggerService;
import com.logvex.crystal.scan.ChestPoint;
import com.logvex.crystal.scan.ChestReport;
import com.logvex.crystal.scan.ChestScanner;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * ChestLogger: counts chests and trapped chests in loaded chunks, groups nearby ones into
 * clusters, and raises a local notification when a cluster is large enough to be worth
 * noting.
 *
 * <p>Everything is local. The scan reads only chunks the client already has loaded, and the
 * result is shown to the player on their own screen (and optionally written to a local log
 * file). No network code exists anywhere in this feature: there is no webhook, no upload,
 * and no transmission of positions off the machine.
 *
 * <p>Cost is bounded two ways: the scan runs every {@code ScanIntervalTicks} rather than
 * every tick, and the clustering runs on the logger's own worker thread so a large haul
 * never stalls a frame. The client thread only walks loaded chunks, which is O(chunks).
 */
public final class ChestLoggerModule extends Module {

    private final IntSetting scanInterval = add("ScanIntervalTicks", "Ticks between scans.", 60, 20, 600);
    private final IntSetting threshold = add("Threshold", "Report clusters with at least this many chests.", 30, 1, 512);
    private final IntSetting linkDistance = add("ClusterRadius", "Max gap between chests in one cluster.", 2, 1, 8);
    private final IntSetting verticalTolerance = add("VerticalTolerance", "Max vertical gap between chests.", 1, 0, 8);
    private final IntSetting minClusterSize = add("MinClusterSize", "Ignore clusters smaller than this.", 2, 1, 16);
    private final IntSetting duplicateRadius = add("DuplicateRadius", "Treat clusters closer than this as the same find.", 16, 4, 64);
    private final IntSetting cooldownSeconds = add("CooldownSeconds", "Minimum seconds between reports of the same find.", 300, 30, 3600);
    private final IntSetting playerRadius = add("PlayerRadius", "Blocks around a cluster to list visible players.", 64, 16, 256);
    private final BooleanSetting notify = add("Notify", "Show a local notification when a cluster is found.", true);
    private final IntSetting chunkRadius = add("ChunkRadius", "Loaded chunks to scan around the player.", 6, 1, 12);

    private final ChestLoggerService service = new ChestLoggerService();
    private long lastScanTick;
    private String lastDimension = "";

    public ChestLoggerModule() {
        super(Category.MISC, "ChestLogger", "Counts chests in loaded chunks and clusters them locally.");
    }

    @Override
    protected void onEnable() {
        pushConfig();
        service.setRunning(true);
        service.reset();
        lastScanTick = 0;
        lastDimension = "";
    }

    @Override
    protected void onDisable() {
        service.setRunning(false);
        service.reset();
    }

    @Override
    public void onDisconnect() {
        service.reset();
        lastDimension = "";
        // Do not disable on disconnect: the module should resume when the next world loads.
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return;
        }
        // Re-apply settings each scan so a change in the ClickGUI takes effect live.
        pushConfig();
        service.onDimensionChange(world.getRegistryKey().getValue().toString());

        long ticks = client.world.getTime();
        if (ticks - lastScanTick < scanInterval.value()) {
            drainReports();
            return;
        }
        lastScanTick = ticks;

        int centerChunkX = client.player.getBlockX() >> 4;
        int centerChunkZ = client.player.getBlockZ() >> 4;
        List<ChestPoint> points = ChestScanner.scan(world, centerChunkX, centerChunkZ, chunkRadius.value());
        List<String> players = visiblePlayerNames(world, client.player.getX(), client.player.getY(),
                client.player.getZ(), playerRadius.value());
        service.submit(world.getRegistryKey().getValue().toString(), points, players, System.currentTimeMillis());
        drainReports();
    }

    private void drainReports() {
        List<ChestReport> reports = service.poll();
        for (ChestReport report : reports) {
            reportLocally(report);
        }
    }

    private void reportLocally(ChestReport report) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null && notify.value()) {
            bootstrap.notify(com.logvex.crystal.scan.ReportPresenter.notificationText(report),
                    NotificationLevel.CUTE);
        }
        CrystalClient.LOG.info("{}", com.logvex.crystal.scan.ReportPresenter.logLine(report));
    }

    private void pushConfig() {
        service.configure(new ChestLoggerService.Config(
                minClusterSize.value(), linkDistance.value(), verticalTolerance.value(),
                threshold.value(), duplicateRadius.value(), cooldownSeconds.value() * 1000L));
    }

    /**
     * Names of players the client already renders, within {@code radius} blocks. Uses only
     * the client's own loaded world — the same set visible on screen.
     */
    private static List<String> visiblePlayerNames(ClientWorld world, double x, double y, double z, double radius) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player == null || player.getGameProfile() == null) {
                continue;
            }
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                names.add(player.getNameForScoreboard());
            }
        }
        return names;
    }

    /** Exposed for the smoke harness: the number of clusters the last scan considered. */
    public ChestLoggerService service() {
        return service;
    }
}
