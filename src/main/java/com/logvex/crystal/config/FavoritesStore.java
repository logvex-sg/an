package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the ClickGUI's favourite modules and its recently-used list to
 * {@code <config>/favorites.json}.
 *
 * <p>Both are ordered, de-duplicated sets of module names. Recents are capped so the list
 * cannot grow without bound. Writes are atomic and the schema is versioned; a corrupt file
 * simply yields empty lists rather than stopping the client. Minecraft-independent, so it
 * is unit tested directly.
 */
public final class FavoritesStore {

    public static final int SCHEMA_VERSION = 1;
    /** How many recently-used modules are remembered. */
    public static final int MAX_RECENT = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Deque<String> recent = new ArrayDeque<>();

    public FavoritesStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public boolean isFavorite(String moduleName) {
        return moduleName != null && favorites.contains(moduleName);
    }

    /** Flips favourite status and returns the new state. */
    public boolean toggleFavorite(String moduleName) {
        if (moduleName == null) {
            return false;
        }
        if (favorites.remove(moduleName)) {
            return false;
        }
        favorites.add(moduleName);
        return true;
    }

    public List<String> favorites() {
        return new ArrayList<>(favorites);
    }

    /** Records that a module was used, moving it to the front of the recents list. */
    public void markUsed(String moduleName) {
        if (moduleName == null) {
            return;
        }
        recent.remove(moduleName);
        recent.addFirst(moduleName);
        while (recent.size() > MAX_RECENT) {
            recent.removeLast();
        }
    }

    public List<String> recent() {
        return new ArrayList<>(recent);
    }

    public void clearRecent() {
        recent.clear();
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
            // A corrupt favourites file must never stop the client from starting.
        }
    }

    public void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(toJson(), writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Serializes the store (exposed for tests). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.add("favorites", toArray(favorites));
        root.add("recent", toArray(recent));
        return root;
    }

    /** Applies a parsed object (exposed for tests). */
    public void apply(JsonObject root) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        favorites.clear();
        recent.clear();
        readStrings(root, "favorites", favorites);
        List<String> recents = new ArrayList<>();
        readStrings(root, "recent", recents);
        for (String name : recents) {
            if (recent.size() < MAX_RECENT && !recent.contains(name)) {
                recent.addLast(name);
            }
        }
    }

    private static com.google.gson.JsonArray toArray(Iterable<String> values) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static void readStrings(JsonObject root, String key, java.util.Collection<String> into) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) {
                into.add(element.getAsString());
            }
        }
    }
}
