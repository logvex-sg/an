package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The starter profiles must be well-formed and name-stable. */
class StarterProfilesTest {

    @Test
    void everyStarterBuildsWithModulesAndAppearance() {
        for (String name : StarterProfiles.names()) {
            ProfileManager.Profile profile = StarterProfiles.build(name);
            assertNotNull(profile, "starter missing: " + name);
            assertTrue(profile.modules().size() > 0, "starter has no modules: " + name);
            assertTrue(profile.appearance().has("preset"), "starter has no preset: " + name);
        }
    }

    @Test
    void unknownStarterIsNull() {
        assertNull(StarterProfiles.build("does-not-exist"));
    }

    @Test
    void minimalHudDisablesTheNoisyElements() {
        ProfileManager.Profile profile = StarterProfiles.build("Minimal HUD");
        assertTrue(profile.modules().getAsJsonObject("ModuleList").get("enabled").getAsBoolean() == false);
        assertTrue(profile.modules().getAsJsonObject("Coords").get("enabled").getAsBoolean());
    }

    @Test
    void startersSerializeToTheProfileShape() {
        ProfileManager.Profile profile = StarterProfiles.build("Combat");
        var json = ProfileManager.toJson(profile);
        assertEquals(ProfileManager.SCHEMA_VERSION, json.get("schema").getAsInt());
        assertNotNull(ProfileManager.fromJson(json));
    }
}
