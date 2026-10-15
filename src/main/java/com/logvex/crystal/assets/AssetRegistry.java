package com.logvex.crystal.assets;

import com.logvex.crystal.client.CrystalClient;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/**
 * Loads the {@link AssetManifest} from the mod's resource pack, caches the registered
 * textures, and degrades gracefully when an asset is missing.
 *
 * <p>Loading happens on the render/client thread, lazily, the first time an asset is asked
 * for, and only once per (id, tint) pair. A missing or malformed file is remembered as
 * failed and every later request returns null, so the caller can fall back to the procedural
 * drawing the client already ships and no repeated disk reads or log spam occur.
 *
 * <p>Tinted icons are re-registered under a {@code tint/<id>/<argb>} identifier. The registry
 * holds at most {@link #MAX_TINTED} tinted variants and clears them on a theme change so the
 * cache cannot grow without bound.
 */
public final class AssetRegistry {

    private static final AssetRegistry INSTANCE = new AssetRegistry();
    private static final int MAX_TINTED = 256;

    private final Map<String, Identifier> plain = new HashMap<>();
    private final Map<String, Identifier> tinted = new HashMap<>();
    private final Map<String, Boolean> failed = new HashMap<>();

    private int registered;
    private int failures;

    private AssetRegistry() {
    }

    public static AssetRegistry get() {
        return INSTANCE;
    }

    /** Number of textures currently registered. */
    public int registered() {
        return registered;
    }

    /** Number of assets that failed to load (missing or malformed). */
    public int failures() {
        return failures;
    }

    /** True when the asset loaded successfully at least once. */
    public boolean isLoaded(String id) {
        return plain.containsKey(id) || tinted.containsKey(id);
    }

    /**
     * Returns the identifier for an image asset, or null when it is missing. The result is
     * cached; a failure is cached too so a missing file is only ever read once.
     */
    public Identifier image(String id) {
        Identifier cached = plain.get(id);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(failed.get(id))) {
            return null;
        }
        AssetManifest.Entry entry = AssetManifest.get(id);
        if (entry == null) {
            failed.put(id, true);
            return null;
        }
        Identifier id2 = loadRaw(entry);
        if (id2 == null) {
            failed.put(id, true);
            failures++;
            return null;
        }
        plain.put(id, id2);
        registered++;
        return id2;
    }

    /**
     * Returns a tinted variant of an icon: the white pixels are multiplied by {@code argb},
     * so one texture follows the active accent. Returns null when the base icon is missing.
     */
    public Identifier icon(String id, int argb) {
        String key = id + "/" + Integer.toHexString(argb);
        Identifier cached = tinted.get(key);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(failed.get(key))) {
            return null;
        }
        NativeImage source = readImage(AssetManifest.get(id));
        if (source == null) {
            failed.put(key, true);
            return null;
        }
        int tint = argb;
        NativeImage copy = source.applyToCopy(pixel -> multiply(pixel, tint));
        source.close();
        if (tinted.size() >= MAX_TINTED) {
            releaseTinted();
        }
        Identifier registeredId = register("tint/" + id + "/" + Integer.toHexString(argb), copy);
        tinted.put(key, registeredId);
        registered++;
        return registeredId;
    }

    /** Drops every tinted variant; call on a theme change so accents refresh. */
    public void releaseTinted() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            for (Identifier id : tinted.values()) {
                client.getTextureManager().destroyTexture(id);
            }
        }
        registered -= tinted.size();
        tinted.clear();
    }

    /** Drops every cached texture. Safe to call at any time; the next draw reloads lazily. */
    public void releaseAll() {
        releaseTinted();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getTextureManager() != null) {
            for (Identifier id : plain.values()) {
                client.getTextureManager().destroyTexture(id);
            }
        }
        registered -= plain.size();
        plain.clear();
        failed.clear();
    }

    /** A reload listener that drops every cached texture so the pack is re-read on a reload. */
    public static final class ReloadListener implements net.minecraft.resource.ResourceReloader {

        /** Stable id used for reload ordering. */
        public static final Identifier ID = Identifier.of(AssetManifest.NAMESPACE, "assets");

        @Override
        public String getName() {
            return "LOGVEX CRYSTAL assets";
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> reload(
                net.minecraft.resource.ResourceReloader.Store store,
                java.util.concurrent.Executor prepareExecutor,
                net.minecraft.resource.ResourceReloader.Synchronizer synchronizer,
                java.util.concurrent.Executor applyExecutor) {
            return synchronizer.whenPrepared(null).thenRunAsync(
                    () -> INSTANCE.releaseAll(), applyExecutor);
        }
    }

    // ------------------------------------------------------------------ internals

    private Identifier loadRaw(AssetManifest.Entry entry) {
        NativeImage image = readImage(entry);
        if (image == null) {
            return null;
        }
        Identifier id = register(entry.path(), image);
        return id;
    }

    private NativeImage readImage(AssetManifest.Entry entry) {
        if (entry == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getResourceManager() == null) {
            return null;
        }
        Identifier location = Identifier.of(AssetManifest.NAMESPACE, entry.path());
        try {
            Resource resource = client.getResourceManager().getResource(location).orElse(null);
            if (resource == null) {
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return NativeImage.read(in);
            }
        } catch (Throwable t) {
            CrystalClient.LOG.warn("Asset {} failed to load", entry.id(), t);
            return null;
        }
    }

    private Identifier register(String path, NativeImage image) {
        Identifier id = Identifier.of(AssetManifest.NAMESPACE, path);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "logvex-crystal " + path, image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return id;
    }

    /** Multiplies a pixel's RGB by the tint, preserving the alpha (icons are white). */
    private static int multiply(int pixel, int tint) {
        int a = (pixel >>> 24) & 0xFF;
        if (a == 0) {
            return 0;
        }
        int r = ((pixel >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((pixel >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (pixel & 0xFF) * (tint & 0xFF) / 255;
        int ta = (tint >>> 24) & 0xFF;
        return ((a * ta / 255) << 24) | (r << 16) | (g << 8) | b;
    }
}
