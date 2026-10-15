package com.logvex.crystal.util.waypoint;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The persisted waypoint list. Same conventions as the other stores: schema-versioned JSON,
 * atomic write, malformed entries skipped rather than aborting the load, and a newer schema
 * refused so a future version's data is not silently downgraded.
 *
 * <p>Deliberately Minecraft-free so the persistence logic is unit tested without a client.
 */
public final class WaypointStore {

    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final List<Waypoint> waypoints = new ArrayList<>();

    public WaypointStore(Path file) {
        this.file = file;
    }

    public List<Waypoint> all() {
        return Collections.unmodifiableList(waypoints);
    }

    public int size() {
        return waypoints.size();
    }

    public Waypoint byName(String name) {
        for (Waypoint waypoint : waypoints) {
            if (waypoint.name().equalsIgnoreCase(name)) {
                return waypoint;
            }
        }
        return null;
    }

    /** Adds or replaces a waypoint with the same (case-insensitive) name. */
    public void put(Waypoint waypoint) {
        waypoints.removeIf(existing -> existing.name().equalsIgnoreCase(waypoint.name()));
        waypoints.add(waypoint);
    }

    public boolean remove(String name) {
        return waypoints.removeIf(waypoint -> waypoint.name().equalsIgnoreCase(name));
    }

    public void clear() {
        waypoints.clear();
    }

    /** The waypoints visible in a dimension, nearest first. */
    public List<Waypoint> nearest(String dimension, double x, double y, double z) {
        List<Waypoint> visible = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.matchesDimension(dimension)) {
                visible.add(waypoint);
            }
        }
        visible.sort((a, b) -> Double.compare(a.distanceSquaredTo(x, y, z), b.distanceSquaredTo(x, y, z)));
        return visible;
    }

    public void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (Waypoint waypoint : waypoints) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", waypoint.name());
            entry.addProperty("x", waypoint.x());
            entry.addProperty("y", waypoint.y());
            entry.addProperty("z", waypoint.z());
            entry.addProperty("color", waypoint.color());
            entry.addProperty("dimension", waypoint.dimension());
            array.add(entry);
        }
        root.add("waypoints", array);
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
            // A corrupt waypoint file must not stop the client from starting.
        }
    }

    /** Applies a parsed root object. Exposed for tests. */
    public void apply(JsonObject root) {
        if (root.has("schema") && root.get("schema").isJsonPrimitive()
                && root.get("schema").getAsInt() > SCHEMA_VERSION) {
            return;
        }
        if (!root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
            return;
        }
        waypoints.clear();
        for (JsonElement element : root.getAsJsonArray("waypoints")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            try {
                String name = entry.get("name").getAsString();
                int x = entry.get("x").getAsInt();
                int y = entry.get("y").getAsInt();
                int z = entry.get("z").getAsInt();
                int color = entry.has("color") ? entry.get("color").getAsInt() : 0xFFFF9EC4;
                String dimension = entry.has("dimension") ? entry.get("dimension").getAsString() : "";
                waypoints.add(new Waypoint(name, x, y, z, color, dimension.toLowerCase(Locale.ROOT)));
            } catch (RuntimeException malformed) {
                // Skip this entry, keep the rest.
            }
        }
    }
}
