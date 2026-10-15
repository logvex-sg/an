package com.logvex.crystal.client;

import com.logvex.crystal.assets.AssetRegistry;
import com.logvex.crystal.combat.CombatService;
import com.logvex.crystal.command.CommandManager;
import com.logvex.crystal.config.AppearanceStore;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.config.FavoritesStore;
import com.logvex.crystal.config.HudLayoutStore;
import com.logvex.crystal.config.ProfileManager;
import com.logvex.crystal.config.ProfileService;
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
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
    private FavoritesStore favoritesStore;
    private ProfileService profileService;
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
    private static final int SMOKE_TICKS = Integer.getInteger("logvex.smoke.ticks", 440);
    /** Optional directory for dev screenshots of the smoke stages. */
    private static final String SHOT_DIR = System.getProperty("logvex.smoke.shots", "");
    private int smokeTick;
    private int smokeStage;

    /** Saves a screenshot of the current framebuffer when the dev shot directory is set. */
    private void shot(String name) {
        if (SHOT_DIR.isEmpty()) {
            return;
        }
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            LOG.info("[shot] {} screen={} tr={} fb={}x{}", name,
                    c.currentScreen == null ? "null" : c.currentScreen.getClass().getSimpleName(),
                    c.textRenderer != null,
                    c.getFramebuffer().textureWidth, c.getFramebuffer().textureHeight);
            java.io.File dir = new java.io.File(SHOT_DIR);
            dir.mkdirs();
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(dir,
                    name + ".png", MinecraftClient.getInstance().getFramebuffer(), 1, text -> {});
        } catch (Throwable t) {
            LOG.warn("smoke screenshot failed: {}", name, t);
        }
    }

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
        // Drop tinted icon variants whenever the palette changes so accents stay correct.
        theme.addListener(palette -> AssetRegistry.get().releaseTinted());
        net.fabricmc.fabric.api.resource.v1.ResourceLoader
                .get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloader(AssetRegistry.ReloadListener.ID, new AssetRegistry.ReloadListener());
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

        favoritesStore = new FavoritesStore(ConfigPaths.favorites());
        favoritesStore.load();
        profileService = new ProfileService(new ProfileManager(ConfigPaths.profiles()), config,
                hudLayoutStore, appearanceStore, hud.layout(), theme.settings(), this::afterConfigApply);

        commands = new CommandManager(this);

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
        // Register the whole Crystal HUD as one layer attached after the vanilla HUD.
        HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
                net.minecraft.util.Identifier.of("logvex-crystal", "hud"),
                this::renderHud);
        // World-space ESP is collected and drawn inside the world render pass, where the
        // camera-relative matrix stack and vertex consumers are valid.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::renderWorldEsp);
    }

    /** The Crystal HUD pass: every enabled HUD element, then the cosmetic particles. */
    private void renderHud(net.minecraft.client.gui.DrawContext context,
                           net.minecraft.client.render.RenderTickCounter tickCounter) {
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
                    clickGui().open();
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
                clickGui().open();
                require(client.currentScreen instanceof ClickGui, "ClickGUI did not install");
                require(((ClickGui) client.currentScreen).isRenderable(),
                        "ClickGUI was installed with a null text renderer");
                smokeStage++;
            }
            case 1 -> {
                if (smokeTick < 70) {
                    return;
                }
                // The ClickGUI has now drawn at least once, so the asset pack must have been
                // resolved from the mod's own resources: prove the textures loaded, not fell
                // back to the procedural motifs.
                var assets = com.logvex.crystal.assets.AssetRegistry.get();
                require(assets.image("logo") != null, "logo.png did not load from the asset pack");
                require(assets.icon("crystal", 0xFFFFFFFF) != null, "crystal icon did not load");
                require(assets.registered() > 0, "no textures were registered");
                require(assets.failures() == 0, "asset pack reported " + assets.failures() + " failures");
                LOG.info("LOGVEX CRYSTAL asset pack: {} assets, {} textures registered",
                        com.logvex.crystal.assets.AssetManifest.size(), assets.registered());
                shot("01-clickgui-combat");
                clickGui().showAppearance();
                require(((ClickGui) client.currentScreen).isAppearance(), "Appearance tab not selected");
                smokeStage++;
            }
            case 2 -> {
                if (smokeTick < 100) {
                    return;
                }
                shot("02-clickgui-appearance");
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
            case 10 -> {
                if (smokeTick < 320) {
                    return;
                }
                // Favourites round-trip through disk. Normalise first: a previous smoke run
                // leaves AutoCrystal favourited on disk, so a bare toggle would flip it off.
                if (favoritesStore.isFavorite("AutoCrystal")) {
                    favoritesStore.toggleFavorite("AutoCrystal");
                }
                require(!favoritesStore.isFavorite("AutoCrystal"), "favourite state was not normalised");
                favoritesStore.toggleFavorite("AutoCrystal");
                require(favoritesStore.isFavorite("AutoCrystal"), "favourite was not recorded");
                favoritesStore.markUsed("AutoTotem");
                favoritesStore.markUsed("AutoCrystal");
                require(favoritesStore.recent().get(0).equals("AutoCrystal"), "recents ordering wrong");
                saveAll();
                require(java.nio.file.Files.exists(ConfigPaths.favorites()), "favorites.json was not written");
                smokeStage++;
            }
            case 11 -> {
                if (smokeTick < 350) {
                    return;
                }
                // Profile save -> mutate -> load round-trip through the real service.
                require(profileService != null, "profile service was not created");
                try {
                    boolean saved = profileService.save("smoke-profile", true);
                    require(saved, "profile was not written");
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("SMOKE FAILURE: profile save threw", e);
                }
                require(java.nio.file.Files.exists(ConfigPaths.profiles().resolve("smoke-profile.json")),
                        "profile file missing");
                // Change something, then load the profile back and prove it was restored.
                var entityEsp = modules.byName("EntityEsp");
                require(entityEsp != null, "EntityEsp missing");
                if (!entityEsp.isEnabled()) {
                    entityEsp.toggle(modules);
                }
                boolean ok = profileService.apply("smoke-profile");
                require(ok, "profile did not load");
                require(favoritesStore.isFavorite("AutoCrystal"), "favourite lost after profile load");
                // Clean up the smoke profile so repeat runs start fresh.
                profileService.profiles().delete("smoke-profile");
                smokeStage++;
            }
            case 12 -> {
                if (smokeTick < 380) {
                    return;
                }
                // Exercise every new screen installing without a null renderer.
                client.setScreen(new com.logvex.crystal.gui.KeybindScreen(modules, theme, null));
                require(client.currentScreen instanceof com.logvex.crystal.gui.KeybindScreen,
                        "KeybindScreen did not install");
                smokeStage++;
            }
            case 13 -> {
                if (smokeTick < 400) {
                    return;
                }
                client.setScreen(new com.logvex.crystal.gui.ProfileScreen(profileService, theme));
                require(client.currentScreen instanceof com.logvex.crystal.gui.ProfileScreen,
                        "ProfileScreen did not install");
                client.setScreen(null);
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
            favoritesStore.save();
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

    /**
     * Runs after a profile is applied: rebuilds the palette from the restored appearance,
     * re-syncs the HUD element visibility and pushes the theme state into the Theme module
     * so its settings do not disagree with the profile.
     */
    private void afterConfigApply() {
        theme.update();
        applyHudVisibility();
        var themeModule = modules.byClass(com.logvex.crystal.module.misc.ThemeModule.class);
        if (themeModule != null) {
            themeModule.syncFromTheme();
        }
    }

    private static void copyInto(com.logvex.crystal.theme.ThemeSettings source,
                                 com.logvex.crystal.theme.ThemeSettings target) {
        ProfileService.copyInto(source, target);
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

    /**
     * The ClickGUI, created on first use.
     *
     * <p>{@code Screen} snapshots {@code MinecraftClient.textRenderer} in its constructor, and
     * that field is still null while {@code onInitializeClient} runs. Building the screen here
     * (once fonts exist) keeps it from being permanently constructed with a null renderer,
     * which would make {@code render()} bail out and draw nothing. The stale-instance guard
     * covers any screen that was nonetheless built early.
     */
    public ClickGui clickGui() {
        if (clickGui == null || !clickGui.isRenderable()) {
            clickGui = new ClickGui(modules, config, hud, theme);
        }
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

    /** Favourite/recent module tracking for the ClickGUI. */
    public FavoritesStore favorites() {
        return favoritesStore;
    }

    /** Named configuration profiles (save/load/import/export). */
    public ProfileService profiles() {
        return profileService;
    }

    public void notify(String message, NotificationLevel level) {
        notifications.notify(message, level);
    }
}
