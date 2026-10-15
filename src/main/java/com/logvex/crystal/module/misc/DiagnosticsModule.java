package com.logvex.crystal.module.misc;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.util.UpdateClock;
import net.minecraft.client.MinecraftClient;

/**
 * Diagnostics: a single command surface that reports the client's own health - module
 * errors, configuration file status, performance counters and version/dependency
 * information - without producing a wall of logging.
 *
 * <p>On enable it prints a compact summary to chat; {@code ; diag} re-prints it on demand
 * (the older {@code ; diagnostics} still shows the module error log). Nothing sensitive is
 * included: no tokens, no server address, no world seed. It is read-only over the client's
 * own state.
 */
public final class DiagnosticsModule extends Module {

    private final BooleanSetting reportOnEnable = add("ReportOnEnable", "Print a summary when enabled.", true);
    private final BooleanSetting watchFiles = add("WatchFiles", "Include config file status.", true);
    private final IntSetting refreshSeconds = add("RefreshSeconds", "How often to re-sample counters.", 2, 1, 30);

    private final UpdateClock clock = new UpdateClock(2000);
    private int lastFps = -1;
    private long lastTickNanos;

    public DiagnosticsModule() {
        super(Category.MISC, "Diagnostics", "Reports client health: errors, files, performance, version.");
    }

    @Override
    protected void onEnable() {
        if (reportOnEnable.value()) {
            report();
        }
    }

    @Override
    protected void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (clock.ready()) {
            lastFps = client.getCurrentFps();
        }
        // Track the slowest module tick since the last report, for the performance line.
        long worst = 0;
        for (Module module : context().moduleManager().all()) {
            worst = Math.max(worst, module.lastTickNanos());
        }
        lastTickNanos = worst;
    }

    /** Builds and posts the diagnostic summary. Public so a command can trigger it. */
    public void report() {
        MinecraftClient client = MinecraftClient.getInstance();
        var errors = CrystalClient.errors();
        context().notifications().info("Diagnostics:");
        context().notifications().info("  modules " + context().moduleManager().size()
                + " (" + context().moduleManager().enabledCount() + " enabled)");
        context().notifications().info(errors.distinctModules() == 0
                ? "  errors: none \u2661"
                : "  errors: " + errors.totalRecorded() + " across " + errors.distinctModules() + " module(s)");
        if (watchFiles.value()) {
            context().notifications().info("  config: " + configStatus());
        }
        context().notifications().info("  perf: fps " + (lastFps < 0 ? client.getCurrentFps() : lastFps)
                + ", worst module tick " + String.format("%.2fms", lastTickNanos / 1_000_000.0));
        context().notifications().info("  version " + version() + ", MC " + client.getGameVersion());
        context().notifications().info("  hud elements " + context().hudManager().elements().size()
                + ", world ESP providers " + (context().hudManager().hasWorldEsp() ? "active" : "idle"));
    }

    /** A one-line summary of which config files exist and which have backups. */
    private String configStatus() {
        int present = 0;
        int backups = 0;
        java.nio.file.Path[] files = {
                ConfigPaths.modules(), ConfigPaths.appearance(), ConfigPaths.hud(),
                ConfigPaths.friends(), ConfigPaths.waypoints(), ConfigPaths.favorites()
        };
        for (java.nio.file.Path file : files) {
            if (java.nio.file.Files.exists(file)) {
                present++;
            }
            if (java.nio.file.Files.exists(com.logvex.crystal.config.ConfigIo.backupPath(file))) {
                backups++;
            }
        }
        return present + "/" + files.length + " files, " + backups + " backup(s)";
    }

    private static String version() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("logvex-crystal")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    /** Exposed for tests: the last sampled FPS, or -1 before the first sample. */
    public int lastFps() {
        return lastFps;
    }

    /** Exposed for tests: the worst module tick time seen, in nanoseconds. */
    public long worstTickNanos() {
        return lastTickNanos;
    }
}
