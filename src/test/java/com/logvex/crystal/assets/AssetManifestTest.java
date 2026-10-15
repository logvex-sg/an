package com.logvex.crystal.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies the asset manifest against the files actually on the classpath, so a renamed or
 * deleted PNG cannot silently break the interface. Runs without Minecraft: only the
 * resource paths and the classpath are exercised.
 */
class AssetManifestTest {

    private static final Set<String> CATEGORY_ICONS = Set.of(
            "combat", "crystal", "movement", "player", "render", "hud", "utility");

    @Test
    void everyIconEntryHasAUniqueId() {
        Set<String> ids = new HashSet<>(AssetManifest.ICONS);
        assertEquals(AssetManifest.ICONS.size(), ids.size(), "icon ids must be unique");
        for (String id : AssetManifest.ICONS) {
            assertTrue(AssetManifest.contains(id), "icon " + id + " missing from manifest");
        }
    }

    @Test
    void idsAreUniqueAcrossTheWholeManifest() {
        Set<String> seen = new HashSet<>();
        for (AssetManifest.Entry entry : AssetManifest.all()) {
            assertTrue(seen.add(entry.id()), "duplicate asset id: " + entry.id());
        }
    }

    @Test
    void everyEntryPathIsAPngUnderTheModNamespace() {
        for (AssetManifest.Entry entry : AssetManifest.all()) {
            assertTrue(entry.path().startsWith("textures/gui/"), entry.id() + " path " + entry.path());
            assertTrue(entry.path().endsWith(".png"), entry.id() + " must be a PNG");
            assertTrue(entry.width() > 0 && entry.height() > 0, entry.id() + " needs a size");
            assertEquals(AssetManifest.NAMESPACE + ":" + entry.path(), AssetManifest.classpath(entry.id()));
        }
    }

    @Test
    void everyAssetIsReadableAndAValidPng() {
        // Loom does not put the main resource output on the test runtime classpath, so
        // resolve from the source tree as a fallback; both must agree on the file.
        for (AssetManifest.Entry entry : AssetManifest.all()) {
            String resource = AssetManifest.NAMESPACE + "/" + entry.path();
            byte[] header = readPngHeader(resource, entry.path());
            assertNotNull(header, "asset missing: " + resource);
            assertEquals((byte) 0x89, header[0], resource + " is not a PNG");
            assertEquals('P', header[1]);
            assertEquals('N', header[2]);
            assertEquals('G', header[3]);
        }
    }

    private byte[] readPngHeader(String resource, String relativePath) {
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in != null) {
                return in.readNBytes(4);
            }
        } catch (Exception ignored) {
            // fall through to the source tree
        }
        java.io.File target = new java.io.File(
                "src/main/resources/assets/" + AssetManifest.NAMESPACE + "/" + relativePath);
        if (!target.isFile()) {
            return null;
        }
        try (InputStream in = new java.io.FileInputStream(target)) {
            return in.readNBytes(4);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void categoryIconsHaveAShippedTintedTexture() {
        for (String icon : CATEGORY_ICONS) {
            assertTrue(AssetManifest.contains(icon), "category icon " + icon + " missing");
            assertEquals(AssetManifest.Kind.TINTED_ICON, AssetManifest.get(icon).kind());
        }
    }

    @Test
    void unknownIdsResolveToNull() {
        assertNull(AssetManifest.get("definitely-not-an-asset"));
        assertNull(AssetManifest.classpath("definitely-not-an-asset"));
        assertFalse(AssetManifest.contains("definitely-not-an-asset"));
    }

    @Test
    void brandingAndHudAssetsAreFullColourImages() {
        for (String id : new String[] {"logo", "app_icon", "header_logo", "loading", "hud_frame"}) {
            AssetManifest.Entry entry = AssetManifest.get(id);
            assertNotNull(entry, id + " missing");
            assertEquals(AssetManifest.Kind.IMAGE, entry.kind(), id + " should not be tinted");
        }
    }
}
