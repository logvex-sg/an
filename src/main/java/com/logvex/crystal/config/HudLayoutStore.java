package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.logvex.crystal.gui.HudLayout;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Persists per-element HUD placement (position, visibility, opacity, scale) to
 * {@code <config>/hud.json}.
 *
 * <p>Kept separate from the module and appearance configs so dragging a HUD element never
 * rewrites either. Writes are atomic and the schema is versioned; unknown or newer files
 * are ignored rather than misread. The codec is Minecraft-independent so it can be tested.
 */
public final class HudLayoutStore {

    /** Schema version of the on-disk format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;

    public HudLayoutStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Reads stored placements into {@code layout}. Absent/corrupt files leave it untouched. */
    public void loadInto(HudLayout layout) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                apply(root.getAsJsonObject(), layout);
            }
        } catch (Exception e) {
            // A corrupt layout must never stop the client from starting.
        }
    }

    /** Writes every entry in {@code layout} atomically. */
    public void save(HudLayout layout) throws IOException {
        JsonObject root = toJson(layout);
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

    /** Serializes a layout (exposed for tests and diagnostics). */
    public static JsonObject toJson(HudLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject elements = new JsonObject();
        for (Map.Entry<String, HudLayout.Entry> e : layout.all().entrySet()) {
            HudLayout.Entry entry = e.getValue();
            JsonObject json = new JsonObject();
            json.addProperty("visible", entry.visible);
            json.addProperty("customPosition", entry.customPosition);
            json.addProperty("x", entry.x);
            json.addProperty("y", entry.y);
            json.addProperty("opacity", entry.opacity);
            json.addProperty("scale", entry.scale);
            elements.add(e.getKey(), json);
        }
        root.add("elements", elements);
        return root;
    }

    /** Applies a parsed layout object. Exposed for tests. */
    public static void apply(JsonObject root, HudLayout layout) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (!root.has("elements") || !root.get("elements").isJsonObject()) {
            return;
        }
        JsonObject elements = root.getAsJsonObject("elements");
        for (String id : elements.keySet()) {
            if (!elements.get(id).isJsonObject()) {
                continue;
            }
            JsonObject json = elements.getAsJsonObject(id);
            HudLayout.Entry entry = layout.entry(id);
            entry.visible = optBool(json, "visible", entry.visible);
            entry.customPosition = optBool(json, "customPosition", entry.customPosition);
            entry.x = optInt(json, "x", entry.x);
            entry.y = optInt(json, "y", entry.y);
            entry.opacity = clamp(optFloat(json, "opacity", entry.opacity), 0f, 1f);
            entry.scale = clamp(optFloat(json, "scale", entry.scale), 0.5f, 2f);
        }
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
