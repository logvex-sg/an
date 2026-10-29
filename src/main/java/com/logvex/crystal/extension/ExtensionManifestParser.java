package com.logvex.crystal.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses an {@link ExtensionManifest} from the {@code custom.logvex.extension} object of a
 * Fabric mod's metadata.
 *
 * <p>Kept separate from the manager so the parsing and validation rules are unit tested without
 * a running game. Parsing never executes extension code; it only reads JSON values, and any
 * missing or wrong-typed field is either defaulted (optional fields) or reported as a
 * validation failure (required fields).
 */
public final class ExtensionManifestParser {

    private ExtensionManifestParser() {
    }

    /**
     * Reads a manifest from the given object, or returns {@code null} when the object is not a
     * manifest at all (missing the required {@code id}). A present-but-malformed manifest throws
     * {@link ExtensionManifest.InvalidManifestException}.
     */
    public static ExtensionManifest parse(JsonObject manifestObject) {
        if (manifestObject == null) {
            return null;
        }
        if (!manifestObject.has("id") || !manifestObject.get("id").isJsonPrimitive()) {
            return null;
        }
        String id = manifestObject.get("id").getAsString();
        String name = string(manifestObject, "name", id);
        String version = string(manifestObject, "version", "");
        int apiMajor = 1;
        int apiMinor = 0;
        if (manifestObject.has("api") && manifestObject.get("api").isJsonObject()) {
            JsonObject api = manifestObject.getAsJsonObject("api");
            apiMajor = intValue(api, "major", 1);
            apiMinor = intValue(api, "minor", 0);
        }
        String minClient = string(manifestObject, "minClient", "");
        Set<String> capabilities = stringSet(manifestObject, "capabilities");
        Set<String> dependencies = stringSet(manifestObject, "dependencies");
        Set<String> optionalDependencies = stringSet(manifestObject, "optionalDependencies");
        String author = string(manifestObject, "author", "");
        String license = string(manifestObject, "license", "");
        return new ExtensionManifest(id, name, version, apiMajor, apiMinor, minClient,
                capabilities, dependencies, optionalDependencies, author, license);
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            return object.get(key).getAsString();
        }
        return fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return object.get(key).getAsInt();
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Set<String> stringSet(JsonObject object, String key) {
        Set<String> result = new LinkedHashSet<>();
        if (object.has(key) && object.get(key).isJsonArray()) {
            JsonArray array = object.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                }
            }
        }
        return result;
    }
}
