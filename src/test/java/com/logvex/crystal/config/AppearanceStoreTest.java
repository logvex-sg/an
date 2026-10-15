package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeSettings;
import com.logvex.crystal.theme.ThemeVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip and resilience tests for the appearance config codec. These run without a
 * Minecraft runtime because the codec is deliberately dependency-free.
 */
class AppearanceStoreTest {

    @Test
    void roundTripsEveryField(@TempDir Path dir) throws IOException {
        AppearanceStore store = new AppearanceStore(dir.resolve("appearance.json"));
        ThemeSettings original = new ThemeSettings();
        original.preset = ThemePreset.MIDNIGHT_KITTY;
        original.variant = ThemeVariant.LIGHT;
        original.primaryOverride = 0xFF112233;
        original.secondaryOverride = 0xFF445566;
        original.backgroundOpacity = 0.42f;
        original.gradientIntensity = 0.31f;
        original.glowIntensity = 0.77f;
        original.cornerRadius = 9;
        original.uiScale = 1.25f;
        original.animationSpeed = 1.75f;
        original.decorations = false;
        original.catCosmetics = false;
        original.reducedMotion = true;
        original.blur = false;
        original.customCursor = false;
        original.guiSounds = false;
        original.splashText = false;
        original.easterEgg = true;

        store.save(original);
        ThemeSettings loaded = store.load();

        assertEquals(ThemePreset.MIDNIGHT_KITTY, loaded.preset);
        assertEquals(ThemeVariant.LIGHT, loaded.variant);
        assertEquals(0xFF112233, loaded.primaryOverride);
        assertEquals(0xFF445566, loaded.secondaryOverride);
        assertEquals(0.42f, loaded.backgroundOpacity, 1e-6);
        assertEquals(0.31f, loaded.gradientIntensity, 1e-6);
        assertEquals(0.77f, loaded.glowIntensity, 1e-6);
        assertEquals(9, loaded.cornerRadius);
        assertEquals(1.25f, loaded.uiScale, 1e-6);
        assertEquals(1.75f, loaded.animationSpeed, 1e-6);
        assertFalse(loaded.decorations);
        assertFalse(loaded.catCosmetics);
        assertTrue(loaded.reducedMotion);
        assertFalse(loaded.blur);
        assertFalse(loaded.customCursor);
        assertFalse(loaded.guiSounds);
        assertFalse(loaded.splashText);
        assertTrue(loaded.easterEgg);
    }

    @Test
    void absentFileYieldsDefaults(@TempDir Path dir) {
        AppearanceStore store = new AppearanceStore(dir.resolve("missing.json"));
        ThemeSettings settings = store.load();
        assertEquals(new ThemeSettings().preset, settings.preset);
        assertEquals(new ThemeSettings().backgroundOpacity, settings.backgroundOpacity, 1e-6);
    }

    @Test
    void corruptFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("appearance.json");
        Files.writeString(file, "{ this is not valid json");
        AppearanceStore store = new AppearanceStore(file);
        ThemeSettings settings = store.load();
        assertEquals(ThemePreset.FEMBOY, settings.preset);
    }

    @Test
    void newerSchemaIsRefused() {
        JsonObject root = AppearanceStore.toJson(new ThemeSettings());
        root.addProperty("schema", AppearanceStore.SCHEMA_VERSION + 1);
        root.addProperty("preset", "SAKURA_PINK");
        ThemeSettings parsed = AppearanceStore.fromJson(root);
        // A future config must not be misinterpreted; defaults are kept.
        assertEquals(ThemePreset.FEMBOY, parsed.preset);
    }

    @Test
    void nullOverridesAreOmittedAndRestoredAsNull() {
        ThemeSettings settings = new ThemeSettings();
        settings.clearOverrides();
        JsonObject json = AppearanceStore.toJson(settings);
        assertFalse(json.has("primaryOverride"));
        ThemeSettings parsed = AppearanceStore.fromJson(json);
        assertNull(parsed.primaryOverride);
        assertNull(parsed.secondaryOverride);
    }

    @Test
    void malformedValuesKeepDefaults() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", AppearanceStore.SCHEMA_VERSION);
        root.addProperty("backgroundOpacity", "not-a-number");
        root.addProperty("preset", "NOPE");
        ThemeSettings parsed = AppearanceStore.fromJson(root);
        assertEquals(new ThemeSettings().backgroundOpacity, parsed.backgroundOpacity, 1e-6);
        assertEquals(ThemePreset.FEMBOY, parsed.preset);
    }

    @Test
    void loadClampsOutOfRangeValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("appearance.json");
        Files.writeString(file, "{\"schema\":1,\"cornerRadius\":500,\"uiScale\":42.0}");
        ThemeSettings loaded = new AppearanceStore(file).load();
        assertEquals(12, loaded.cornerRadius);
        assertEquals(1.5f, loaded.uiScale, 1e-6);
    }
}
