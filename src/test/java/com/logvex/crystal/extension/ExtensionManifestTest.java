package com.logvex.crystal.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers manifest parsing and validation - the gate before any extension code runs. */
class ExtensionManifestTest {

    @Test
    void parsesACompleteManifest() {
        JsonObject object = JsonParser.parseString("""
                {
                  "id": "example-waypoints",
                  "name": "Example Waypoints",
                  "version": "1.2.3",
                  "api": { "major": 1, "minor": 0 },
                  "minClient": "1.0.0",
                  "capabilities": ["waypoints", "hud"],
                  "dependencies": ["core"],
                  "optionalDependencies": ["baritone"],
                  "author": "someone",
                  "license": "MIT"
                }
                """).getAsJsonObject();
        ExtensionManifest manifest = ExtensionManifestParser.parse(object);
        assertEquals("example-waypoints", manifest.id());
        assertEquals("Example Waypoints", manifest.name());
        assertEquals("1.2.3", manifest.version());
        assertEquals(1, manifest.apiMajor());
        assertEquals(0, manifest.apiMinor());
        assertEquals(Set.of("waypoints", "hud"), manifest.capabilities());
        assertEquals(Set.of("core"), manifest.dependencies());
        assertEquals(Set.of("baritone"), manifest.optionalDependencies());
        assertEquals("MIT", manifest.license());
        manifest.validate();
    }

    @Test
    void defaultsApiWhenAbsent() {
        JsonObject object = JsonParser.parseString("{\"id\":\"x\",\"version\":\"1\"}").getAsJsonObject();
        ExtensionManifest manifest = ExtensionManifestParser.parse(object);
        assertEquals(1, manifest.apiMajor());
        assertEquals(0, manifest.apiMinor());
        // Name falls back to the id when absent.
        assertEquals("x", manifest.name());
    }

    @Test
    void anObjectWithoutAnIdIsNotAManifest() {
        JsonObject object = JsonParser.parseString("{\"name\":\"nope\"}").getAsJsonObject();
        assertNull(ExtensionManifestParser.parse(object));
        assertNull(ExtensionManifestParser.parse(null));
    }

    @Test
    void validationRejectsBadIdsAndMissingVersions() {
        ExtensionManifest noVersion = ExtensionManifest.minimal("ok", "Ok", "", 1, 0);
        assertThrows(ExtensionManifest.InvalidManifestException.class, noVersion::validate);

        ExtensionManifest badId = ExtensionManifest.minimal("Bad Id!", "Bad", "1", 1, 0);
        assertThrows(ExtensionManifest.InvalidManifestException.class, badId::validate);

        ExtensionManifest blankId = ExtensionManifest.minimal("", "Blank", "1", 1, 0);
        assertThrows(ExtensionManifest.InvalidManifestException.class, blankId::validate);
    }

    @Test
    void idIsNormalisedToLowerCase() {
        ExtensionManifest manifest = ExtensionManifest.minimal("Example", "Example", "1", 1, 0);
        assertEquals("example", manifest.id());
    }

    @Test
    void compatibilityUsesTheApiRule() {
        assertTrue(ExtensionManifest.minimal("a", "a", "1", 1, 0).compatible());
        ExtensionManifest tooNew = ExtensionManifest.minimal("b", "b", "1", 2, 0);
        assertFalse(tooNew.compatible());
        assertFalse(tooNew.incompatibilityReason().isEmpty());
    }
}
