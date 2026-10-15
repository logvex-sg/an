package com.logvex.crystal.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Round-trip and robustness tests for the notification preferences codec. */
class NotificationStoreTest {

    @Test
    void roundTripsAllFields() {
        NotificationSettings settings = new NotificationSettings();
        settings.durationMillis = 8000;
        settings.scale = 1.5f;
        settings.position = NotificationSettings.Position.BOTTOM_LEFT;
        settings.sound = false;
        settings.suppressDuplicates = false;
        settings.duplicateWindowMillis = 1000;
        settings.maxVisible = 4;
        settings.setShown(NotificationLevel.WARN, false);
        settings.setShown(NotificationLevel.CUTE, false);

        NotificationSettings restored = new NotificationSettings();
        NotificationStore.apply(NotificationStore.toJson(settings), restored);

        assertEquals(8000, restored.durationMillis);
        assertEquals(1.5f, restored.scale, 1e-6);
        assertEquals(NotificationSettings.Position.BOTTOM_LEFT, restored.position);
        assertFalse(restored.sound);
        assertFalse(restored.suppressDuplicates);
        assertEquals(1000, restored.duplicateWindowMillis);
        assertEquals(4, restored.maxVisible);
        assertFalse(restored.shows(NotificationLevel.WARN));
        assertFalse(restored.shows(NotificationLevel.CUTE));
        assertTrue(restored.shows(NotificationLevel.INFO));
    }

    @Test
    void newerSchemaIsIgnored() {
        NotificationSettings settings = new NotificationSettings();
        JsonObject root = NotificationStore.toJson(settings);
        root.addProperty("schema", NotificationStore.SCHEMA_VERSION + 1);
        root.addProperty("durationMillis", 1);
        NotificationSettings target = new NotificationSettings();
        NotificationStore.apply(root, target);
        assertEquals(5200, target.durationMillis);
    }

    @Test
    void outOfRangeValuesAreClamped() {
        NotificationSettings settings = new NotificationSettings();
        JsonObject root = JsonParser.parseString("{\"schema\":1,\"scale\":9.0,\"durationMillis\":10,"
                + "\"maxVisible\":999}").getAsJsonObject();
        NotificationStore.apply(root, settings);
        assertEquals(2.0f, settings.scale, 1e-6);
        assertEquals(1000, settings.durationMillis);
        assertEquals(16, settings.maxVisible);
    }

    @Test
    void unknownPositionKeepsTheDefault() {
        NotificationSettings settings = new NotificationSettings();
        JsonObject root = JsonParser.parseString("{\"schema\":1,\"position\":\"MIDDLE\"}").getAsJsonObject();
        NotificationStore.apply(root, settings);
        assertEquals(NotificationSettings.Position.TOP_RIGHT, settings.position);
    }

    @Test
    void copyIsDeep() {
        NotificationSettings settings = new NotificationSettings();
        settings.setShown(NotificationLevel.ERROR, false);
        NotificationSettings copy = settings.copy();
        copy.setShown(NotificationLevel.ERROR, true);
        assertFalse(settings.shows(NotificationLevel.ERROR), "the original must not change");
    }
}
