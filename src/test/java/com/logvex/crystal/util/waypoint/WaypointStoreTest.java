package com.logvex.crystal.util.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence, lookup and ordering tests for the waypoint store. */
class WaypointStoreTest {

    @Test
    void putReplacesByNameCaseInsensitively() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Base", 1, 2, 3, 0xFFFF0000, ""));
        store.put(new Waypoint("base", 4, 5, 6, 0xFFFF0000, ""));
        assertEquals(1, store.size());
        assertEquals(4, store.byName("BASE").x());
    }

    @Test
    void removeIsCaseInsensitive() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Stash", 0, 0, 0, 0xFFFFFFFF, ""));
        assertTrue(store.remove("STASH"));
        assertFalse(store.remove("stash"));
        assertEquals(0, store.size());
    }

    @Test
    void nearestFiltersByDimensionAndSortsByDistance() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Far", 100, 0, 0, 0xFFFFFFFF, "minecraft:overworld"));
        store.put(new Waypoint("Near", 5, 0, 0, 0xFFFFFFFF, "minecraft:overworld"));
        store.put(new Waypoint("Nether", 1, 0, 0, 0xFFFFFFFF, "minecraft:the_nether"));
        store.put(new Waypoint("Anywhere", 3, 0, 0, 0xFFFFFFFF, ""));

        var visible = store.nearest("minecraft:overworld", 0, 0, 0);
        assertEquals(3, visible.size(), "overworld + dimension-agnostic entries");
        assertEquals("Anywhere", visible.get(0).name());
        assertEquals("Near", visible.get(1).name());
        assertEquals("Far", visible.get(2).name());
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("waypoints.json");
        WaypointStore original = new WaypointStore(file);
        original.put(new Waypoint("Home", 10, 64, -20, 0xFF112233, "minecraft:overworld"));
        original.put(new Waypoint("Portal", 0, 70, 0, 0xFF445566, ""));
        original.save();

        WaypointStore loaded = new WaypointStore(file);
        loaded.load();
        assertEquals(2, loaded.size());
        Waypoint home = loaded.byName("home");
        assertNotNull(home);
        assertEquals(10, home.x());
        assertEquals(64, home.y());
        assertEquals(-20, home.z());
        assertEquals(0xFF112233, home.color());
        assertEquals("minecraft:overworld", home.dimension());
    }

    @Test
    void corruptFileLeavesStoreEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("waypoints.json");
        Files.writeString(file, "not json at all {{{");
        WaypointStore store = new WaypointStore(file);
        store.load();
        assertEquals(0, store.size());
    }

    @Test
    void newerSchemaIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("waypoints.json");
        Files.writeString(file, "{\"schema\":999,\"waypoints\":[{\"name\":\"x\",\"x\":1,\"y\":2,\"z\":3}]}");
        WaypointStore store = new WaypointStore(file);
        store.load();
        assertEquals(0, store.size(), "a future schema must not be silently downgraded");
    }

    @Test
    void malformedEntryIsSkippedButOthersLoad() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        JsonObject root = JsonParser.parseString("""
                {"schema":1,"waypoints":[
                  {"name":"good","x":1,"y":2,"z":3},
                  {"x":1,"y":2,"z":3},
                  {"name":"alsoGood","x":4,"y":5,"z":6}
                ]}""").getAsJsonObject();
        store.apply(root);
        assertEquals(2, store.size());
        assertNotNull(store.byName("good"));
        assertNotNull(store.byName("alsoGood"));
    }

    @Test
    void dimensionMatchingRules() {
        Waypoint any = new Waypoint("any", 0, 0, 0, 0xFFFFFFFF, "");
        Waypoint overworld = new Waypoint("ow", 0, 0, 0, 0xFFFFFFFF, "minecraft:overworld");
        assertTrue(any.matchesDimension("minecraft:the_nether"));
        assertTrue(overworld.matchesDimension("minecraft:overworld"));
        assertFalse(overworld.matchesDimension("minecraft:the_end"));
    }

    @Test
    void distanceSquaredIsExact() {
        Waypoint waypoint = new Waypoint("p", 3, 4, 0, 0xFFFFFFFF, "");
        assertEquals(25.0, waypoint.distanceSquaredTo(0, 0, 0), 1e-9);
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Waypoint("  ", 0, 0, 0, 0, ""));
    }
}
