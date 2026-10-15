package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.theme.ThemePreset;
import java.util.List;

/**
 * Curated starter profiles shipped with the client.
 *
 * <p>Each is built as plain JSON in the same shape a saved profile uses, so applying one
 * goes through exactly the same path as any user profile. Only enabled flags and the theme
 * are set; unknown module names are skipped by the loader, so a starter keeps working even
 * if a module is renamed or removed.
 *
 * <p>These are templates the user loads, never auto-applied - installing the client never
 * silently changes the user's configuration.
 */
public final class StarterProfiles {

    private StarterProfiles() {
    }

    /** Names of every starter, in display order. */
    public static List<String> names() {
        return List.of("Combat", "Anarchy Survival", "Minimal HUD", "Pastel Aesthetics");
    }

    /** Builds the named starter, or null when the name is unknown. */
    public static ProfileManager.Profile build(String name) {
        return switch (name) {
            case "Combat" -> combat();
            case "Anarchy Survival" -> anarchy();
            case "Minimal HUD" -> minimalHud();
            case "Pastel Aesthetics" -> pastel();
            default -> null;
        };
    }

    private static ProfileManager.Profile combat() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AutoCrystal", "AutoTotem", "CritAssist", "Sprint",
                "CrystalEsp", "EntityEsp", "Tracers", "TargetHud", "CrystalDamage"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("combat", modules, new JsonObject(),
                appearance(ThemePreset.COTTON_CANDY, true));
    }

    private static ProfileManager.Profile anarchy() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"AntiAfk", "AutoReconnect", "ChestLogger", "StorageEsp",
                "Search", "WaypointEsp", "Coords", "Watermark", "AutoEat", "FastPlace"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("anarchy-survival", modules, new JsonObject(),
                appearance(ThemePreset.MIDNIGHT_KITTY, true));
    }

    private static ProfileManager.Profile minimalHud() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Coords", "Watermark"}) {
            modules.add(name, enabled(true));
        }
        for (String name : new String[]{"ModuleList", "TargetHud", "CrystalDamage", "Performance"}) {
            modules.add(name, enabled(false));
        }
        return new ProfileManager.Profile("minimal-hud", modules, new JsonObject(),
                appearance(ThemePreset.LAVENDER_DREAM, false));
    }

    private static ProfileManager.Profile pastel() {
        JsonObject modules = new JsonObject();
        for (String name : new String[]{"Watermark", "Coords", "TargetHud", "CrystalDamage"}) {
            modules.add(name, enabled(true));
        }
        return new ProfileManager.Profile("pastel-aesthetics", modules, new JsonObject(),
                appearance(ThemePreset.SAKURA_FEMBOY, true));
    }

    /** One module entry: {@code {"enabled": bool, "settings": {}}}. */
    private static JsonObject enabled(boolean value) {
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", value);
        entry.add("settings", new JsonObject());
        return entry;
    }

    private static JsonObject appearance(ThemePreset preset, boolean decorations) {
        JsonObject appearance = new JsonObject();
        appearance.addProperty("schema", AppearanceStore.SCHEMA_VERSION);
        appearance.addProperty("preset", preset.name());
        appearance.addProperty("variant", "DARK");
        appearance.addProperty("decorations", decorations);
        return appearance;
    }
}
