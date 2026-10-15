package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round-trip and capping tests for the favourites / recents store. */
class FavoritesStoreTest {

    @Test
    void favouritesToggleAndPersist(@TempDir Path dir) throws IOException {
        FavoritesStore store = new FavoritesStore(dir.resolve("favorites.json"));
        assertTrue(store.toggleFavorite("AutoCrystal"));
        assertTrue(store.isFavorite("AutoCrystal"));
        assertFalse(store.toggleFavorite("AutoCrystal"));
        assertFalse(store.isFavorite("AutoCrystal"));

        store.toggleFavorite("AutoTotem");
        store.save();

        FavoritesStore reloaded = new FavoritesStore(dir.resolve("favorites.json"));
        reloaded.load();
        assertTrue(reloaded.isFavorite("AutoTotem"));
    }

    @Test
    void recentsMoveToFrontAndAreCapped(@TempDir Path dir) {
        FavoritesStore store = new FavoritesStore(dir.resolve("favorites.json"));
        for (int i = 0; i < FavoritesStore.MAX_RECENT + 5; i++) {
            store.markUsed("Module" + i);
        }
        assertEquals(FavoritesStore.MAX_RECENT, store.recent().size());
        // The most recently used is first.
        assertEquals("Module" + (FavoritesStore.MAX_RECENT + 4), store.recent().get(0));
        // Re-using an existing entry moves it to the front without duplicating.
        store.markUsed("Module5");
        assertEquals("Module5", store.recent().get(0));
        assertEquals(store.recent().indexOf("Module5"), store.recent().lastIndexOf("Module5"));
    }

    @Test
    void corruptFileLeavesEmptyLists(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("favorites.json");
        Files.writeString(file, "nonsense");
        FavoritesStore store = new FavoritesStore(file);
        store.load();
        assertTrue(store.favorites().isEmpty());
        assertTrue(store.recent().isEmpty());
    }

    @Test
    void newerSchemaIsIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("favorites.json");
        Files.writeString(file, "{\"schema\":" + (FavoritesStore.SCHEMA_VERSION + 1)
                + ",\"favorites\":[\"X\"]}");
        FavoritesStore store = new FavoritesStore(file);
        store.load();
        assertTrue(store.favorites().isEmpty(), "a newer-schema file must not be read");
    }
}
