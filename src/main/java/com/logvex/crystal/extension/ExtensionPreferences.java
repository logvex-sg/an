package com.logvex.crystal.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persists which extensions the user has approved for activation, to
 * {@code <config>/extensions.json}.
 *
 * <p>Only the <em>decision</em> is stored - a set of extension ids - never any executable
 * content. This is what makes activation survive a restart without ever auto-running a newly
 * discovered extension: an id is only present here because the user enabled it in a previous
 * session. A schema version guards the file and a newer schema is refused rather than
 * half-read.
 */
public final class ExtensionPreferences {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final Set<String> enabled = new LinkedHashSet<>();

    public ExtensionPreferences(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** True when the user has approved the given extension id for activation. */
    public boolean isApproved(String id) {
        return id != null && enabled.contains(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Records approval; returns true when the set changed. */
    public boolean approve(String id) {
        return id != null && enabled.add(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Removes approval; returns true when the set changed. */
    public boolean revoke(String id) {
        return id != null && enabled.remove(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public Set<String> approved() {
        return Set.copyOf(enabled);
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        enabled.clear();
        if (root.has("enabled") && root.get("enabled").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("enabled")) {
                if (element.isJsonPrimitive()) {
                    enabled.add(element.getAsString().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (String id : enabled) {
            array.add(id);
        }
        root.add("enabled", array);
        ConfigIo.writeObject(file, root);
    }
}
