package com.logvex.crystal.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                apply(root.getAsJsonObject());
            }
        } catch (Exception e) {
            // A corrupt friend list must not stop the client from starting.
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
