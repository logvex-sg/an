package com.logvex.crystal.client;

import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.command.CommandManager;
import com.logvex.crystal.config.AppearanceStore;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.config.HudLayoutStore;
import com.logvex.crystal.cosmetic.CosmeticService;
import com.logvex.crystal.cosmetic.GuiSounds;
import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.HudManager;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.ModuleContext;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.module.ModuleRegistry;
import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.FrameClock;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint and composition root.
 *
 * <p>Builds the service graph once (theme → notifications → modules → HUD → commands),
 * wires the Fabric callbacks, then ticks and renders everything from those callbacks.
 * Nothing is done at class-load time; all setup happens in {@link #onInitializeClient()}.
 *
 * <p>This is a purely client-side mod: it reads world state and draws an interface. It
 * performs no external process interaction.
 */
public final class CrystalClient implements ClientModInitializer {

    public static final Logger LOG = LoggerFactory.getLogger("LogvexCrystal");

    private static CrystalClient instance;

    private ThemeManager theme;
    private NotificationManager notifications;
    private ModuleManager modules;
    private HudManager hud;
    private CombatService combat;
    private CosmeticService cosmetics;
    private ConfigManager config;
    private AppearanceStore appearanceStore;
    private HudLayoutStore hudLayoutStore;
    private CommandManager commands;
    private ClickGui clickGui;
    private KeyBinding clickGuiKey;
    private final com.logvex.crystal.social.FriendList friends =
            new com.logvex.crystal.social.FriendList(ConfigPaths.friends());
    private final com.logvex.crystal.util.waypoint.WaypointStore waypoints =
            new com.logvex.crystal.util.waypoint.WaypointStore(ConfigPaths.waypoints());
    private final com.logvex.crystal.render.esp.WorldEspCollector worldEsp =
            new com.logvex.crystal.render.esp.WorldEspCollector();

    private boolean wasInWorld;
    private long lastSaveMillis;

    /** Opt-in headless driver: {@code -Dlogvex.smoke=true -Dlogvex.smoke.ticks=N}. */
    private static final boolean SMOKE = Boolean.getBoolean("logvex.smoke");
    private static final int SMOKE_TICKS = Integer.getInteger("logvex.smoke.ticks", 340);
    private int smokeTick;
    private int smokeStage;

    /** The live bootstrap, or null before the client has initialised. */
    public static CrystalClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        LOG.info("LOGVEX CRYSTAL initialising");

        theme = new ThemeManager();
        appearanceStore = new AppearanceStore(ConfigPaths.appearance());
        // Load appearance before anything reads a palette.
        copyInto(appearanceStore.load(), theme.settings());
        theme.update();
        friends.load();
        waypoints.load();

        notifications = new NotificationManager();
        notifications.addListener(notification -> GuiSounds.notifyCute());
        modules = new ModuleManager();
        combat = new CombatService();
        cosmetics = new CosmeticService();
        ModuleRegistry.registerAll(modules);
        config = new ConfigManager(modules, ConfigPaths.modules());
        hud = new HudManager(modules, theme);
        hudLayoutStore = new HudLayoutStore(ConfigPaths.hud());
        hudLayoutStore.loadInto(hud.layout());
        modules.attachContext(new ModuleContext(modules, combat, theme, hud, notifications, config));
        config.load();
        applyHudVisibility();

        commands = new CommandManager(this);
        clickGui = new ClickGui(modules, config, hud, theme);

        registerKeybind();
        registerCallbacks();

        // Cute startup notice, deferred until fonts exist.
        notifications.cute("Logvex Crystal loaded \u2661");
        LOG.info("LOGVEX CRYSTAL ready: {} modules, theme '{}'", modules.size(), theme.palette().preset().label());
    }

    private void registerKeybind() {
        clickGuiKey = new KeyBinding("key.logvex-crystal.clickgui", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.MISC);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(clickGuiKey);
    }

    private void registerCallbacks() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        // Intercept the local command prefix before the line is sent to the server. Anarchy
        // servers reject unknown slash commands, so Crystal commands never leave the client.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !commands.handleChat(message));
        ClientSendMessageEvents.ALLOW_COMMAND.register(message -> !commands.handleChat("." + message));
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!ScreenGuard.fontsReady()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            FrameClock.tick(client);
            // While a screen is open the HUD is drawn beneath it as a live preview; the
            // HudEditor screen adds its own drag frames on top.
            if (client.currentScreen == null || client.currentScreen instanceof com.logvex.crystal.gui.ClickGui
                    || client.currentScreen instanceof com.logvex.crystal.gui.HudEditorScreen) {
                hud.render(context, client, tickCounter.getDynamicDeltaTicks());
            }
            cosmetics.renderParticles(context, client);
        });
        // World-space ESP is collected and drawn inside the world render pass, where the
        // camera-relative matrix stack and vertex consumers are valid.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderWorldEsp);
    }

    /**
     * Collects every ESP module's primitives into one reused buffer and draws them in a
     * single pass. Bails out early when nothing is registered or no world is loaded, so the
     * hook costs nothing when ESP is off.
     */
    private void renderWorldEsp(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        if (!hud.hasWorldEsp()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        try {
            worldEsp.clear();
            hud.collectWorldEsp(worldEsp, client);
            com.logvex.crystal.render.esp.WorldEspRenderer.render(context, client,
                    context.gameRenderer().getCamera(), worldEsp);
        } catch (Throwable t) {
            LOG.error("World ESP render failed", t);
        }
    }

    private void onEndTick(MinecraftClient client) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        try {
            combat.tick(client);
            modules.tickAll();
            notifications.prune();
            cosmetics.tick(client);
            while (clickGuiKey.wasPressed()) {
                if (!(client.currentScreen instanceof ClickGui)) {
                    clickGui.open();
                }
            }
            handleWorldTransitions(client);
            autoSave();
            if (SMOKE) {
                runSmokeStep(client);
            }
        } catch (Throwable t) {
            LOG.error("Uncaught error in client tick", t);
        }
    }

    /**
     * The headless GUI smoke sequence. Exercises the screens and theme switching that the
     * normal tick path cannot reach without a human, and fails loudly if a screen fails to
     * install or a palette fails to rebuild. Enabled only by the smoke system property.
     */
    private void runSmokeStep(MinecraftClient client) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        smokeTick++;
        switch (smokeStage) {
            case 0 -> {
                if (smokeTick < 40) {
                    return;
                }
                clickGui.open();
                require(client.currentScreen instanceof ClickGui, "ClickGUI did not install");
                smokeStage++;
            }
            case 1 -> {
                if (smokeTick < 70) {
                    return;
                }
                clickGui.showAppearance();
                require(((ClickGui) client.currentScreen).isAppearance(), "Appearance tab not selected");
                smokeStage++;
            }
            case 2 -> {
                if (smokeTick < 100) {
                    return;
                }
                for (com.logvex.crystal.theme.ThemePreset preset : com.logvex.crystal.theme.ThemePreset.values()) {
                    theme.applyPreset(preset);
                    require(theme.palette().preset() == preset, "Palette did not switch to " + preset);
                }
                smokeStage++;
            }
            case 3 -> {
                if (smokeTick < 130) {
                    return;
                }
                client.setScreen(null);
                // With no world loaded the client installs a TitleScreen, so only assert
                // that the ClickGUI is no longer the active screen.
                require(!(client.currentScreen instanceof ClickGui), "ClickGUI did not close");
                smokeStage++;
            }
            case 4 -> {
                if (smokeTick < 160) {
                    return;
                }
                client.setScreen(new com.logvex.crystal.gui.HudEditorScreen(hud, theme));
                require(client.currentScreen instanceof com.logvex.crystal.gui.HudEditorScreen,
                        "HUD editor did not install");
                smokeStage++;
            }
            case 5 -> {
                if (smokeTick < 190) {
                    return;
                }
                client.setScreen(null);
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.appearance()),
                        "appearance.json was not written");
                smokeStage++;
            }
            case 6 -> {
                if (smokeTick < 220) {
                    return;
                }
                // Prove the HUD layout survives a save/reload round trip.
                var entry = hud.layout().entry("watermark");
                entry.customPosition = true;
                entry.x = 33;
                entry.opacity = 0.4f;
                entry.scale = 1.5f;
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.hud()), "hud.json was not written");
                // Wipe the in-memory values, then reload from disk.
                hud.layout().clear();
                reloadAll();
                var reloaded = hud.layout().entry("watermark");
                require(reloaded.customPosition && reloaded.x == 33,
                        "HUD layout position did not persist");
                require(Math.abs(reloaded.opacity - 0.4f) < 1e-4 && Math.abs(reloaded.scale - 1.5f) < 1e-4,
                        "HUD layout opacity/scale did not persist");
                smokeStage++;
            }
            case 7 -> {
                if (smokeTick < 250) {
                    return;
                }
                // Toggle the ESP modules to prove their enable/disable path (and world-ESP
                // provider registration) runs without a world loaded.
                for (String esp : new String[]{"EntityEsp", "CrystalEsp", "StorageEsp", "HoleEsp"}) {
                    var module = modules.byName(esp);
                    require(module != null, "ESP module not registered: " + esp);
                    module.toggle(modules);
                    require(module.isEnabled(), esp + " did not enable");
                    module.toggle(modules);
                    require(!module.isEnabled(), esp + " did not disable");
                }
                require(!hud.hasWorldEsp(), "world ESP providers were not released on disable");
                smokeStage++;
            }
            case 8 -> {
                if (smokeTick < 280) {
                    return;
                }
                // The friend list must round-trip through disk as well.
                friends.add("SmokeFriend");
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.friends()), "friends.json was not written");
                friends.remove("SmokeFriend");
                saveAll();
                friends.load();
                require(!friends.isFriend("SmokeFriend"), "friend removal did not persist");
                // Waypoints must round-trip too.
                waypoints.put(new com.logvex.crystal.util.waypoint.Waypoint("SmokeSpot", 1, 2, 3,
                        0xFFFF00FF, ""));
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.waypoints()), "waypoints.json was not written");
                waypoints.clear();
                waypoints.load();
                require(waypoints.byName("SmokeSpot") != null, "waypoint did not persist");
                waypoints.clear();
                saveAll();
                smokeStage++;
            }
            case 9 -> {
                if (smokeTick < 300) {
                    return;
                }
                // ChestLogger: prove the module enables, clusters a synthetic haul through
                // its own worker, and delivers exactly one local notification - with no
                // world loaded, which also proves the disabled/idle path is safe.
                var chestLogger = modules.byName("ChestLogger");
                require(chestLogger != null, "ChestLogger module not registered");
                chestLogger.toggle(modules);
                require(chestLogger.isEnabled(), "ChestLogger did not enable");
                var scan = (com.logvex.crystal.module.misc.ChestLoggerModule) chestLogger;
                java.util.List<com.logvex.crystal.scan.ChestPoint> haul = new java.util.ArrayList<>();
                for (int i = 0; i < 30; i++) {
                    haul.add(new com.logvex.crystal.scan.ChestPoint(i, 64, 0));
                }
                scan.service().submit("minecraft:overworld", haul, java.util.List.of(), 1_000L);
                var reports = awaitReports(scan.service(), 1);
                require(reports.size() == 1, "ChestLogger did not produce exactly one report");
                require(reports.get(0).chestCount() == 30, "ChestLogger counted the wrong number of chests");
                // A second identical submission must be suppressed as a duplicate.
                scan.service().submit("minecraft:overworld", haul, java.util.List.of(), 2_000L);
                sleepQuietly(200L);
                require(scan.service().poll().isEmpty(), "ChestLogger duplicate was not suppressed");
                chestLogger.toggle(modules);
                require(!chestLogger.isEnabled(), "ChestLogger did not disable");
                smokeStage++;
            }
            default -> {
                if (smokeTick >= SMOKE_TICKS) {
                    LOG.info("LOGVEX CRYSTAL smoke OK after {} ticks ({} modules)", smokeTick, modules.size());
                    client.scheduleStop();
                }
            }
        }
    }

    /** Polls the logger until it has produced {@code expected} reports, or a short timeout. */
    private static java.util.List<com.logvex.crystal.scan.ChestReport> awaitReports(
            com.logvex.crystal.scan.ChestLoggerService service, int expected) {
        java.util.List<com.logvex.crystal.scan.ChestReport> collected = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 50; attempt++) {
            collected.addAll(service.poll());
            if (collected.size() >= expected) {
                return collected;
            }
            sleepQuietly(10L);
        }
        return collected;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("SMOKE FAILURE: " + message);
        }
    }

    private void handleWorldTransitions(MinecraftClient client) {
        boolean inWorld = client.player != null && client.world != null;
        if (wasInWorld && !inWorld) {
            modules.onDisconnect();
            notifications.clear();
        }
        wasInWorld = inWorld;
    }

    private void autoSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMillis < 60_000L) {
            return;
        }
        lastSaveMillis = now;
        saveAll();
    }

    /** Persists appearance, HUD layout and module state. Safe to call from the client thread. */
    public void saveAll() {
        try {
            appearanceStore.save(theme.settings());
            hudLayoutStore.save(hud.layout());
            config.save();
            friends.save();
            waypoints.save();
        } catch (IOException e) {
            LOG.warn("Failed to save configuration", e);
        }
    }

    /** Reloads appearance + module state from disk and re-applies it live. */
    public void reloadAll() {
        copyInto(appearanceStore.load(), theme.settings());
        theme.update();
        hudLayoutStore.loadInto(hud.layout());
        config.load();
        friends.load();
        waypoints.load();
        applyHudVisibility();
    }

    /** Makes the HUD element visibility match the enabled state of the HUD toggle modules. */
    private void applyHudVisibility() {
        for (com.logvex.crystal.module.Module module : modules.all()) {
            if (module instanceof com.logvex.crystal.module.hud.HudToggleModule toggle) {
                hud.setElementVisible(toggle.elementId(), module.isEnabled());
            }
        }
    }

    private static void copyInto(com.logvex.crystal.theme.ThemeSettings source,
                                 com.logvex.crystal.theme.ThemeSettings target) {
        com.logvex.crystal.theme.ThemeSettings copy = source.copy();
        target.preset = copy.preset;
        target.variant = copy.variant;
        target.primaryOverride = copy.primaryOverride;
        target.secondaryOverride = copy.secondaryOverride;
        target.backgroundOpacity = copy.backgroundOpacity;
        target.gradientIntensity = copy.gradientIntensity;
        target.glowIntensity = copy.glowIntensity;
        target.cornerRadius = copy.cornerRadius;
        target.uiScale = copy.uiScale;
        target.animationSpeed = copy.animationSpeed;
        target.decorations = copy.decorations;
        target.catCosmetics = copy.catCosmetics;
        target.reducedMotion = copy.reducedMotion;
        target.blur = copy.blur;
        target.customCursor = copy.customCursor;
        target.guiSounds = copy.guiSounds;
        target.splashText = copy.splashText;
        target.easterEgg = copy.easterEgg;
    }

    // ------------------------------------------------------------ accessors

    public ThemeManager theme() {
        return theme;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public ModuleManager modules() {
        return modules;
    }

    public HudManager hud() {
        return hud;
    }

    public CombatService combat() {
        return combat;
    }

    public CosmeticService cosmetics() {
        return cosmetics;
    }

    public ConfigManager config() {
        return config;
    }

    public CommandManager commands() {
        return commands;
    }

    public ClickGui clickGui() {
        return clickGui;
    }

    /** The persistent friend list, used to colour ESP and exclude friends from targeting. */
    public com.logvex.crystal.social.FriendList friends() {
        return friends;
    }

    /** The persisted waypoint list. */
    public com.logvex.crystal.util.waypoint.WaypointStore waypoints() {
        return waypoints;
    }

    public void notify(String message, NotificationLevel level) {
        notifications.notify(message, level);
    }
}
