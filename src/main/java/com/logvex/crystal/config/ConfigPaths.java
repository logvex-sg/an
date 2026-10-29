package com.logvex.crystal.config;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Resolves the client's configuration directory ({@code <game>/config/logvex-crystal/}). */
public final class ConfigPaths {

    private ConfigPaths() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("logvex-crystal");
    }

    public static Path appearance() {
        return directory().resolve("appearance.json");
    }

    public static Path modules() {
        return directory().resolve("modules.json");
    }

    public static Path hud() {
        return directory().resolve("hud.json");
    }

    public static Path friends() {
        return directory().resolve("friends.json");
    }

    public static Path waypoints() {
        return directory().resolve("waypoints.json");
    }

    public static Path favorites() {
        return directory().resolve("favorites.json");
    }

    public static Path notifications() {
        return directory().resolve("notifications.json");
    }

    public static Path context() {
        return directory().resolve("context.json");
    }

    /** Persisted automation rules. */
    public static Path automation() {
        return directory().resolve("automation.json");
    }

    /** Persisted extension approval decisions. */
    public static Path extensions() {
        return directory().resolve("extensions.json");
    }

    /** Directory holding named configuration profiles. */
    public static Path profiles() {
        return directory().resolve("profiles");
    }
}
