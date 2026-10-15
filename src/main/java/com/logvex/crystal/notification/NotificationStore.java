package com.logvex.crystal.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists the {@link NotificationSettings} to {@code <config>/notifications.json}.
 *
 * <p>Same conventions as the other stores: schema-versioned, atomic write, a malformed or
 * newer file leaves the defaults in place rather than stopping the client. Minecraft-free,
 * so it is unit tested directly.
 */
public final class NotificationStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public NotificationStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void save(NotificationSettings settings) throws IOException {
        ConfigIo.writeObject(file, toJson(settings));
    }

    public void loadInto(NotificationSettings settings) {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root, settings);
        }
    }

    /** Serializes settings (exposed for tests and profiles). */
    public static JsonObject toJson(NotificationSettings settings) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("durationMillis", settings.durationMillis);
        root.addProperty("scale", settings.scale);
        root.addProperty("position", settings.position.name());
        root.addProperty("sound", settings.sound);
        root.addProperty("suppressDuplicates", settings.suppressDuplicates);
        root.addProperty("duplicateWindowMillis", settings.duplicateWindowMillis);
        root.addProperty("maxVisible", settings.maxVisible);
        JsonArray hidden = new JsonArray();
        for (NotificationLevel level : settings.hiddenLevels()) {
            hidden.add(level.name());
        }
        root.add("hiddenLevels", hidden);
        return root;
    }

    /** Applies a parsed object onto the settings, ignoring a newer schema. */
    public static void apply(JsonObject root, NotificationSettings settings) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (root.has("durationMillis")) {
            settings.durationMillis = root.get("durationMillis").getAsLong();
        }
        if (root.has("scale")) {
            settings.scale = root.get("scale").getAsFloat();
        }
        if (root.has("position")) {
            try {
                settings.position = NotificationSettings.Position.valueOf(root.get("position").getAsString());
            } catch (IllegalArgumentException ignored) {
                // Keep the default for an unknown position.
            }
        }
        if (root.has("sound")) {
            settings.sound = root.get("sound").getAsBoolean();
        }
        if (root.has("suppressDuplicates")) {
            settings.suppressDuplicates = root.get("suppressDuplicates").getAsBoolean();
        }
        if (root.has("duplicateWindowMillis")) {
            settings.duplicateWindowMillis = root.get("duplicateWindowMillis").getAsLong();
        }
        if (root.has("maxVisible")) {
            settings.maxVisible = root.get("maxVisible").getAsInt();
        }
        if (root.has("hiddenLevels") && root.get("hiddenLevels").isJsonArray()) {
            for (NotificationLevel level : NotificationLevel.values()) {
                settings.setShown(level, true);
            }
            for (JsonElement element : root.getAsJsonArray("hiddenLevels")) {
                if (element.isJsonPrimitive()) {
                    try {
                        settings.setShown(NotificationLevel.valueOf(element.getAsString()), false);
                    } catch (IllegalArgumentException ignored) {
                        // Skip an unknown level name.
                    }
                }
            }
        }
        settings.clamp();
    }
}
