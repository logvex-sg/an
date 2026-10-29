package com.logvex.crystal.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.logvex.crystal.api.LogvexExtension;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * Discovers extensions from the Fabric mods that are already loaded.
 *
 * <p>An extension is a normal Fabric mod whose {@code fabric.mod.json} carries a
 * {@code custom.logvex.extension} manifest and that declares a
 * {@code logvex-crystal-extension} entrypoint. Discovery reads metadata and entrypoint
 * <em>containers</em> only; it never instantiates an entrypoint. The entry point is created
 * lazily, the first time the user enables the extension, by a supplier held in the candidate.
 *
 * <p>This is the whole "discovery" story: there is no scanning of arbitrary downloaded files,
 * because the client only ever runs code the Fabric loader already accepted as a mod.
 */
public final class FabricExtensionDiscovery {

    /** The entrypoint key an extension registers under. */
    public static final String ENTRYPOINT_KEY = "logvex-crystal-extension";

    private FabricExtensionDiscovery() {
    }

    /** Discovers every candidate currently loaded, without running any of them. */
    public static List<ExtensionCandidate> discover(FabricLoader loader) {
        List<ExtensionCandidate> result = new ArrayList<>();
        for (ModContainer container : loader.getAllMods()) {
            ModMetadata metadata = container.getMetadata();
            if (!metadata.containsCustomValue(ExtensionManifest.CUSTOM_KEY)) {
                continue;
            }
            JsonObject root = toJson(metadata.getCustomValue(ExtensionManifest.CUSTOM_KEY));
            if (root == null || !root.has("extension") || !root.get("extension").isJsonObject()) {
                continue;
            }
            ExtensionManifest manifest = ExtensionManifestParser.parse(root.getAsJsonObject("extension"));
            if (manifest == null) {
                continue;
            }
            String modId = metadata.getId();
            result.add(new ExtensionCandidate(modId, manifest, loaderSupplier(loader, modId)));
        }
        return result;
    }

    /** A lazy loader that instantiates the extension entrypoint only when first called. */
    private static java.util.function.Supplier<LogvexExtension> loaderSupplier(FabricLoader loader, String modId) {
        return () -> {
            for (EntrypointContainer<LogvexExtension> container
                    : loader.getEntrypointContainers(ENTRYPOINT_KEY, LogvexExtension.class)) {
                if (container.getProvider().getMetadata().getId().equals(modId)) {
                    return container.getEntrypoint();
                }
            }
            throw new IllegalStateException("mod '" + modId
                    + "' declares a logvex extension manifest but no '" + ENTRYPOINT_KEY + "' entrypoint");
        };
    }

    /** Converts a Fabric {@link CustomValue} tree into a Gson element. */
    private static JsonObject toJson(CustomValue value) {
        JsonElement element = convert(value);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonElement convert(CustomValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getType()) {
            case OBJECT -> {
                JsonObject object = new JsonObject();
                for (var entry : value.getAsObject()) {
                    object.add(entry.getKey(), convert(entry.getValue()));
                }
                yield object;
            }
            case ARRAY -> {
                JsonArray array = new JsonArray();
                for (CustomValue item : value.getAsArray()) {
                    array.add(convert(item));
                }
                yield array;
            }
            case STRING -> new JsonPrimitive(value.getAsString());
            case NUMBER -> new JsonPrimitive(value.getAsNumber());
            case BOOLEAN -> new JsonPrimitive(value.getAsBoolean());
            case NULL -> com.google.gson.JsonNull.INSTANCE;
        };
    }
}
