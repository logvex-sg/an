package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the shared config IO: tolerant reads, atomic writes and the backup copy. */
class ConfigIoTest {

    @Test
    void readsAbsentFileAsNull(@TempDir Path dir) {
        assertNull(ConfigIo.readObject(dir.resolve("missing.json")));
    }

    @Test
    void readsMalformedFileAsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        assertNull(ConfigIo.readObject(file));
    }

    @Test
    void readsNonObjectAsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("array.json");
        Files.writeString(file, "[1,2,3]", StandardCharsets.UTF_8);
        assertNull(ConfigIo.readObject(file));
    }

    @Test
    void writeThenReadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject root = new JsonObject();
        root.addProperty("value", 42);
        ConfigIo.writeObject(file, root);

        JsonObject read = ConfigIo.readObject(file);
        assertNotNull(read);
        assertEquals(42, read.get("value").getAsInt());
    }

    @Test
    void writeLeavesNoTempFileBehind(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        ConfigIo.writeObject(file, new JsonObject());
        assertFalse(Files.exists(dir.resolve("data.json.tmp")));
    }

    @Test
    void secondWriteKeepsBackupOfTheFirst(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("data.json");
        JsonObject first = new JsonObject();
        first.addProperty("generation", 1);
        ConfigIo.writeObject(file, first);

        JsonObject second = new JsonObject();
        second.addProperty("generation", 2);
        ConfigIo.writeObject(file, second);

        assertTrue(Files.exists(ConfigIo.backupPath(file)), "backup should exist after an overwrite");
        assertEquals(1, ConfigIo.readObject(ConfigIo.backupPath(file)).get("generation").getAsInt());
        assertEquals(2, ConfigIo.readObject(file).get("generation").getAsInt());
    }

    @Test
    void firstWriteHasNoBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("fresh.json");
        ConfigIo.writeObject(file, new JsonObject());
        assertFalse(Files.exists(ConfigIo.backupPath(file)), "nothing to back up on the first write");
    }

    @Test
    void writeCreatesMissingParentDirectories(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("a/b/c/data.json");
        ConfigIo.writeObject(nested, new JsonObject());
        assertTrue(Files.exists(nested));
    }
}
