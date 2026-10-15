package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behavioural tests for the profile store: round-trips, the no-silent-overwrite rule,
 * corrupt-file recovery and import/export. Minecraft-independent, so no client runtime.
 */
class ProfileManagerTest {

    private ProfileManager.Profile sample(String name) {
        JsonObject modules = new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("enabled", true);
        modules.add("AutoCrystal", entry);
        JsonObject appearance = new JsonObject();
        appearance.addProperty("schema", 1);
        appearance.addProperty("preset", "SAKURA_FEMBOY");
        return new ProfileManager.Profile(name, modules, new JsonObject(), appearance);
    }

    @Test
    void roundTripsAllSections(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        assertTrue(manager.write(sample("combat"), false));

        ProfileManager.Profile loaded = manager.read("combat");
        assertNotNull(loaded);
        assertTrue(loaded.modules().has("AutoCrystal"));
        assertEquals("SAKURA_FEMBOY", loaded.appearance().get("preset").getAsString());
    }

    @Test
    void refusesToOverwriteWithoutOptIn(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        assertTrue(manager.write(sample("combat"), false));
        // A second write of the same name must be refused, not silently clobber.
        assertFalse(manager.write(sample("combat"), false));
        // Explicit overwrite is allowed.
        assertTrue(manager.write(sample("combat"), true));
    }

    @Test
    void corruptFileIsQuarantinedAndNotLost(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        Path file = manager.fileFor("broken");
        Files.createDirectories(dir);
        Files.writeString(file, "{ this is not valid json");

        assertNull(manager.read("broken"), "corrupt profile should read as null");
        assertTrue(Files.exists(dir.resolve("broken.json.corrupt")),
                "corrupt file should be moved aside, not deleted");
    }

    @Test
    void newerSchemaIsRefused(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        JsonObject root = ProfileManager.toJson(sample("future"));
        root.addProperty("schema", ProfileManager.SCHEMA_VERSION + 1);
        Files.createDirectories(dir);
        Files.writeString(manager.fileFor("future"), root.toString());
        assertNull(manager.read("future"), "a newer-schema profile must not be misread");
    }

    @Test
    void exportImportRoundTrip(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        manager.write(sample("combat"), false);
        JsonObject exported = ProfileManager.toJson(manager.read("combat"));
        ProfileManager.Profile parsed = ProfileManager.fromJson(exported);
        assertNotNull(parsed);
        assertTrue(parsed.modules().has("AutoCrystal"));
    }

    @Test
    void duplicateAndRenameRespectClashes(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        manager.write(sample("combat"), false);
        assertTrue(manager.duplicate("combat", "combat-2"));
        assertTrue(manager.exists("combat-2"));
        // Duplicating onto an existing name is refused.
        assertFalse(manager.duplicate("combat", "combat-2"));
        // Renaming onto an existing name is refused.
        assertFalse(manager.rename("combat", "combat-2"));
    }

    @Test
    void exportedDocumentsAreNotListedAsProfiles(@TempDir Path dir) throws IOException {
        ProfileManager manager = new ProfileManager(dir);
        manager.write(sample("combat"), false);
        Files.writeString(dir.resolve("combat" + ProfileManager.EXPORT_SUFFIX), "{}");
        assertEquals(List.of("combat"), manager.names());
    }

    @Test
    void namesAreSanitized(@TempDir Path dir) {
        assertEquals("my-profile", ProfileManager.sanitize("My Profile"));
        // Path separators and dots are stripped, so a traversal attempt cannot escape.
        String traversal = ProfileManager.sanitize("../../etc");
        assertFalse(traversal.contains("/"));
        assertFalse(traversal.contains(".."));
        String weird = ProfileManager.sanitize("weird/../name");
        assertFalse(weird.contains("/"));
        assertFalse(weird.contains(".."));
        assertEquals("profile", ProfileManager.sanitize("!!!"));
    }
}
