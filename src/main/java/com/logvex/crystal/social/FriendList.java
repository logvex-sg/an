package com.logvex.crystal.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The player's friend list: names treated as friendly for ESP colouring and (optionally)
 * excluded from targeting. Persisted to {@code <config>/friends.json} with an atomic write
 * and a schema version, so a crash mid-save cannot corrupt it.
 *
 * <p>Names are matched case-insensitively. The list is small and read on the render thread,
 * so lookups take a snapshot copy of the underlying set.
 */
public final class FriendList {

    public static final int SCHEMA_VERSION = 1;


    private final Set<String> names = new LinkedHashSet<>();
    private final Path file;

    public FriendList(Path file) {
        this.file = file;
    }

    public boolean isFriend(String name) {
        return name != null && names.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Adds a friend. Returns false if the name was already present. */
    public boolean add(String name) {
        return name != null && !name.isBlank() && names.add(name.toLowerCase(Locale.ROOT));
    }

    /** Removes a friend. Returns false if the name was not present. */
    public boolean remove(String name) {
        return name != null && names.remove(name.toLowerCase(Locale.ROOT));
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(names);
    }

    public int size() {
        return names.size();
    }

    public Path file() {
        return file;
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (String name : names) {
            array.add(name);
        }
        root.add("friends", array);
        ConfigIo.writeObject(file, root);
    }

    public void load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root != null) {
            apply(root);
        }
    }

    /** Applies a parsed friend object. Exposed for tests. */
    public void apply(JsonObject root) {
        if (!root.has("friends") || !root.get("friends").isJsonArray()) {
            return;
        }
        names.clear();
        for (JsonElement element : root.getAsJsonArray("friends")) {
            if (element.isJsonPrimitive()) {
                add(element.getAsString());
            }
        }
    }
}
