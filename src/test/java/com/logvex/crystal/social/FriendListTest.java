package com.logvex.crystal.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the friend list's case-insensitive matching and its JSON persistence, including
 * resilience to a corrupt file.
 */
class FriendListTest {

    @Test
    void addRemoveAndMembershipAreCaseInsensitive() {
        FriendList list = new FriendList(Path.of("unused.json"));
        assertTrue(list.add("Notch"));
        assertTrue(list.isFriend("notch"));
        assertTrue(list.isFriend("NOTCH"));
        assertFalse(list.isFriend("Herobrine"));
        assertFalse(list.add("notch"), "duplicate add should be a no-op");
        assertEquals(1, list.size());
        assertTrue(list.remove("NOTCH"));
        assertFalse(list.isFriend("notch"));
    }

    @Test
    void blankNamesAreRejected() {
        FriendList list = new FriendList(Path.of("unused.json"));
        assertFalse(list.add(null));
        assertFalse(list.add("   "));
        assertEquals(0, list.size());
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("friends.json");
        FriendList original = new FriendList(file);
        original.add("Alice");
        original.add("Bob");
        original.save();

        FriendList loaded = new FriendList(file);
        loaded.load();
        assertEquals(2, loaded.size());
        assertTrue(loaded.isFriend("alice"));
        assertTrue(loaded.isFriend("BOB"));
    }

    @Test
    void corruptFileLeavesListEmptyAndDoesNotThrow(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("friends.json");
        Files.writeString(file, "{ this is not valid json ");
        FriendList list = new FriendList(file);
        list.load();
        assertEquals(0, list.size());
    }

    @Test
    void missingFileIsFine(@TempDir Path dir) {
        FriendList list = new FriendList(dir.resolve("does-not-exist.json"));
        list.load();
        assertEquals(0, list.size());
    }
}
