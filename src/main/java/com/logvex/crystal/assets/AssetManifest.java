package com.logvex.crystal.assets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarative inventory of every shipped interface asset.
 *
 * <p>Each entry names a stable id, its classpath path, the file kind and the pixel size, so
 * the loader, the diagnostics screen and the unit tests all agree on what exists without
 * touching the filesystem. This class deliberately imports no Minecraft types: the paths
 * and the id/path mapping are the contract, and they are verified by a plain JUnit test.
 *
 * <p>Icons are authored as white shapes on transparent, then tinted with the active palette
 * at load time, so one texture per icon serves every theme variant.
 */
public final class AssetManifest {

    /** Root of every asset this mod ships. */
    public static final String NAMESPACE = "logvex-crystal";

    /** What an asset is, which decides how the registry loads it. */
    public enum Kind {
        /** A white-on-transparent icon tinted with the active accent at load time. */
        TINTED_ICON,
        /** A full-colour image used as authored. */
        IMAGE
    }

    /** One asset: stable id, classpath path, kind and pixel dimensions. */
    public record Entry(String id, String path, Kind kind, int width, int height) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private AssetManifest() {
    }

    /** Every entry, in a stable order (icons first, then branding, decor and HUD). */
    public static List<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /** Looks up an entry by id, or null when unknown. */
    public static Entry get(String id) {
        return ENTRIES.get(id);
    }

    public static boolean contains(String id) {
        return ENTRIES.containsKey(id);
    }

    public static int size() {
        return ENTRIES.size();
    }

    private static void icon(String id) {
        ENTRIES.put(id, new Entry(id, gui("icons/" + id), Kind.TINTED_ICON, 32, 32));
    }

    private static void image(String id, String relative, int width, int height) {
        ENTRIES.put(id, new Entry(id, gui(relative), Kind.IMAGE, width, height));
    }

    /** A full-colour entity texture (not under {@code textures/gui/}). */
    private static void texture(String id, String relative, int width, int height) {
        ENTRIES.put(id, new Entry(id, "textures/entity/" + relative + ".png", Kind.IMAGE, width, height));
    }

    private static String gui(String relative) {
        return "textures/gui/" + relative + ".png";
    }

    /** Every GUI icon id, in the order the generator writes them. */
    public static final List<String> ICONS = List.of(
            "combat", "crystal", "movement", "player", "render", "world", "inventory",
            "utility", "hud", "settings", "profiles", "keybinds", "search", "notifications",
            "waypoints", "storage", "hole", "entity", "autocrystal", "surround", "totem",
            "chestlogger", "theme", "import", "export", "reset", "add", "remove", "edit",
            "rename", "copy", "confirm", "cancel", "open", "close", "expand", "collapse",
            "warning", "error", "save", "load", "star", "heart", "paw", "sparkle", "bow", "cat",
            "recent", "help");

    static {
        for (String id : ICONS) {
            icon(id);
        }
        image("logo", "branding/logo", 384, 128);
        image("app_icon", "branding/app_icon", 128, 128);
        image("header_logo", "branding/header_logo", 220, 40);
        image("loading", "branding/loading", 320, 180);
        image("empty_state", "branding/empty_state", 160, 120);
        image("watermark_cat", "branding/watermark_cat", 96, 48);
        image("palette_swatch", "branding/palette_swatch", 360, 40);
        image("status_success", "branding/status_success", 16, 16);
        image("status_warn", "branding/status_warn", 16, 16);
        image("status_error", "branding/status_error", 16, 16);
        image("status_info", "branding/status_info", 16, 16);

        image("particle_heart", "decor/particle_heart", 16, 16);
        image("particle_sparkle", "decor/particle_sparkle", 16, 16);
        image("particle_paw", "decor/particle_paw", 16, 16);
        image("particle_star", "decor/particle_star", 16, 16);
        image("gradient_pink_blue", "decor/gradient_pink_blue", 64, 64);
        image("glow_soft", "decor/glow_soft", 64, 64);
        image("bg_pattern", "decor/bg_pattern", 64, 64);

        image("hud_frame", "hud/frame", 180, 56);
        image("hud_health_bar", "hud/health_bar", 100, 8);
        image("hud_armor_bar", "hud/armor_bar", 100, 8);
        image("hud_totem", "hud/totem", 20, 20);
        image("hud_crystal_marker", "hud/crystal_marker", 20, 20);
        image("hud_marker_ring", "hud/marker_ring", 24, 24);
        image("hud_marker_ring_cyan", "hud/marker_ring_cyan", 24, 24);
        image("hud_notification_badge", "hud/notification_badge", 16, 16);
        image("hud_fps", "hud/fps", 14, 14);
        image("hud_ping", "hud/ping", 14, 14);

        texture("player_accessories", "cosmetic/player_accessories", 64, 64);
    }

    /** The full classpath path of an asset, e.g. {@code logvex-crystal:textures/gui/icons/combat.png}. */
    public static String classpath(String id) {
        Entry entry = ENTRIES.get(id);
        return entry == null ? null : NAMESPACE + ":" + entry.path();
    }
}
