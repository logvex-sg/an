package com.logvex.crystal.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists contextual-profile rules to {@code <config>/context.json}.
 *
 * <p>Writes are atomic and the schema is versioned; a malformed rule is skipped rather than
 * failing the whole file, and a newer schema is refused. The codec is Minecraft-independent
 * so the on-disk format is unit tested directly.
 */
public final class ContextStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public ContextStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void loadInto(ContextProfileService service) {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root, service);
        }
    }

    public void save(ContextProfileService service) throws IOException {
        ConfigIo.writeObject(file, toJson(service));
    }

    /** Serializes a service (exposed for tests). */
    public static JsonObject toJson(ContextProfileService service) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("enabled", service.enabled());
        root.addProperty("cooldownMillis", service.cooldownMillis());
        JsonArray rules = new JsonArray();
        for (ContextProfileBinding binding : service.bindings()) {
            JsonObject json = new JsonObject();
            json.addProperty("profile", binding.profileName());
            json.addProperty("enabled", binding.enabled());
            json.addProperty("dimension", binding.rule().dimension());
            json.addProperty("serverContains", binding.rule().serverContains());
            json.addProperty("healthBelow", binding.rule().healthBelow());
            rules.add(json);
        }
        root.add("rules", rules);
        return root;
    }

    /** Applies a parsed object (exposed for tests). */
    public static void apply(JsonObject root, ContextProfileService service) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        service.clear();
        service.setEnabled(optBool(root, "enabled", false));
        service.setCooldownMillis(optLong(root, "cooldownMillis", ContextProfileService.DEFAULT_COOLDOWN_MILLIS));
        if (!root.has("rules") || !root.get("rules").isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray("rules")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            String profile = optString(json, "profile", "");
            if (profile.isBlank()) {
                continue;
            }
            ContextRule rule = new ContextRule(optString(json, "dimension", ""),
                    optString(json, "serverContains", ""), optDouble(json, "healthBelow", 0));
            service.add(new ContextProfileBinding(profile, rule, optBool(json, "enabled", true)));
        }
    }

    private static String optString(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
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

    private static long optLong(JsonObject o, String key, long fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double optDouble(JsonObject o, String key, double fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
