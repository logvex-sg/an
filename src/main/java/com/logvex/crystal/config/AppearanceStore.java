package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeSettings;
import com.logvex.crystal.theme.ThemeVariant;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Persists the appearance settings to {@code <config>/appearance.json}.
 *
 * <p>Writes are atomic (temp file + move) so a crash mid-save cannot corrupt the theme,
 * and unknown/newer schema versions are refused rather than silently misinterpreted. The
 * codec is deliberately independent of Minecraft so it can be unit tested.
 */
public final class AppearanceStore {

    /** Schema version of the on-disk format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public AppearanceStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Loads settings from disk, or returns defaults when the file is absent/malformed. */
    public ThemeSettings load() {
        if (!Files.exists(file)) {
            return new ThemeSettings();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return new ThemeSettings();
            }
            return fromJson(root.getAsJsonObject());
        } catch (Exception e) {
            // A corrupt config must never stop the client from starting; fall back to defaults.
            return new ThemeSettings();
        }
    }

    /** Writes settings atomically. */
    public void save(ThemeSettings settings) throws IOException {
        settings.clamp();
        JsonObject root = toJson(settings);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Serializes settings to a JSON object (exposed for tests and diagnostics). */
    public static JsonObject toJson(ThemeSettings s) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("preset", s.preset.name());
        root.addProperty("variant", s.variant.name());
        if (s.primaryOverride != null) {
            root.addProperty("primaryOverride", s.primaryOverride);
        }
        if (s.secondaryOverride != null) {
            root.addProperty("secondaryOverride", s.secondaryOverride);
        }
        root.addProperty("backgroundOpacity", s.backgroundOpacity);
        root.addProperty("gradientIntensity", s.gradientIntensity);
        root.addProperty("glowIntensity", s.glowIntensity);
        root.addProperty("cornerRadius", s.cornerRadius);
        root.addProperty("uiScale", s.uiScale);
        root.addProperty("animationSpeed", s.animationSpeed);
        root.addProperty("decorations", s.decorations);
        root.addProperty("catCosmetics", s.catCosmetics);
        root.addProperty("reducedMotion", s.reducedMotion);
        root.addProperty("blur", s.blur);
        root.addProperty("customCursor", s.customCursor);
        root.addProperty("guiSounds", s.guiSounds);
        root.addProperty("splashText", s.splashText);
        root.addProperty("easterEgg", s.easterEgg);
        root.addProperty("guiX", s.guiX);
        root.addProperty("guiY", s.guiY);
        return root;
    }

    /** Parses a JSON object into settings, tolerating missing or malformed fields. */
    public static ThemeSettings fromJson(JsonObject root) {
        ThemeSettings s = new ThemeSettings();
        int schema = optInt(root, "schema", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            // A config written by a newer build: keep defaults rather than guess at fields.
            return s;
        }
        if (root.has("preset")) {
            s.preset = ThemePreset.parse(root.get("preset").getAsString());
        }
        if (root.has("variant")) {
            s.variant = ThemeVariant.parse(root.get("variant").getAsString());
        }
        if (root.has("primaryOverride") && !root.get("primaryOverride").isJsonNull()) {
            s.primaryOverride = root.get("primaryOverride").getAsInt();
        }
        if (root.has("secondaryOverride") && !root.get("secondaryOverride").isJsonNull()) {
            s.secondaryOverride = root.get("secondaryOverride").getAsInt();
        }
        s.backgroundOpacity = optFloat(root, "backgroundOpacity", s.backgroundOpacity);
        s.gradientIntensity = optFloat(root, "gradientIntensity", s.gradientIntensity);
        s.glowIntensity = optFloat(root, "glowIntensity", s.glowIntensity);
        s.cornerRadius = optInt(root, "cornerRadius", s.cornerRadius);
        s.uiScale = optFloat(root, "uiScale", s.uiScale);
        s.animationSpeed = optFloat(root, "animationSpeed", s.animationSpeed);
        s.decorations = optBool(root, "decorations", s.decorations);
        s.catCosmetics = optBool(root, "catCosmetics", s.catCosmetics);
        s.reducedMotion = optBool(root, "reducedMotion", s.reducedMotion);
        s.blur = optBool(root, "blur", s.blur);
        s.customCursor = optBool(root, "customCursor", s.customCursor);
        s.guiSounds = optBool(root, "guiSounds", s.guiSounds);
        s.splashText = optBool(root, "splashText", s.splashText);
        s.easterEgg = optBool(root, "easterEgg", s.easterEgg);
        s.guiX = optInt(root, "guiX", s.guiX);
        s.guiY = optInt(root, "guiY", s.guiY);
        s.clamp();
        return s;
    }

    private static int optInt(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float optFloat(JsonObject o, String key, float fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean optBool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
