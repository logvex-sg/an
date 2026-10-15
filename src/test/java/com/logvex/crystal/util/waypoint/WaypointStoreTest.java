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

    @Test
    void duplicateOfFindsANearbySameDimensionWaypoint() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Base", 100, 64, 200, 0xFFFFFFFF, "minecraft:overworld"));
        Waypoint near = new Waypoint("BaseCopy", 102, 64, 201, 0xFFFFFFFF, "minecraft:overworld");
        assertNotNull(store.duplicateOf(near, 4));
        // A waypoint in another dimension is not a duplicate of an overworld one.
        Waypoint nether = new Waypoint("Portal", 100, 64, 200, 0xFFFFFFFF, "minecraft:the_nether");
        assertEquals(null, store.duplicateOf(nether, 4));
        // The same name is never its own duplicate.
        assertEquals(null, store.duplicateOf(new Waypoint("Base", 100, 64, 200, 0xFFFFFFFF,
                "minecraft:overworld"), 4));
    }

    @Test
    void searchMatchesNamesCaseInsensitively() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Home Base", 0, 0, 0, 0xFFFFFFFF, ""));
        store.put(new Waypoint("Mine", 0, 0, 0, 0xFFFFFFFF, ""));
        assertEquals(1, store.search("home").size());
        assertEquals(2, store.search("").size());
        assertEquals(0, store.search("zzz").size());
    }

    @Test
    void importMergesWithoutOverwritingAndIgnoresMalformed() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Existing", 1, 1, 1, 0xFFFFFFFF, ""));
        JsonObject document = JsonParser.parseString("{\"schema\":1,\"waypoints\":["
                + "{\"name\":\"Existing\",\"x\":9,\"y\":9,\"z\":9},"
                + "{\"name\":\"Imported\",\"x\":2,\"y\":3,\"z\":4,\"dimension\":\"minecraft:overworld\"}]}")
                .getAsJsonObject();
        assertEquals(1, store.importFrom(document));
        assertEquals(1, store.byName("Existing").x(), "an import must not overwrite an existing name");
        assertNotNull(store.byName("Imported"));
        assertEquals(0, store.importFrom(null));
    }

    @Test
    void roundTripsThroughJson() {
        WaypointStore store = new WaypointStore(Path.of("unused.json"));
        store.put(new Waypoint("Round", 7, 8, 9, 0xFF123456, "minecraft:the_end"));
        WaypointStore restored = new WaypointStore(Path.of("unused.json"));
        restored.apply(store.toJson());
        assertEquals(1, restored.size());
        assertEquals(0xFF123456, restored.byName("Round").color());
    }
}
