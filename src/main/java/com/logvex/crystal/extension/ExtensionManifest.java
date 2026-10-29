package com.logvex.crystal.extension;

import com.logvex.crystal.api.ApiVersion;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A validated extension manifest.
 *
 * <p>The manifest is read from the extension mod's {@code fabric.mod.json} custom values under
 * the {@code logvex} key, so no second file format or download step is introduced: an extension
 * is a Fabric mod, and its manifest is the metadata Fabric already parses.
 *
 * <p>Example custom block:
 * <pre>{@code
 * "custom": {
 *   "logvex": {
 *     "extension": {
 *       "id": "example-waypoints",
 *       "name": "Example Waypoints",
 *       "version": "1.0.0",
 *       "api": { "major": 1, "minor": 0 },
 *       "minClient": "1.0.0",
 *       "capabilities": ["waypoints", "hud"],
 *       "optionalDependencies": ["baritone"],
 *       "author": "someone",
 *       "license": "MIT"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>{@link #validate()} enforces the invariants a manifest must satisfy before the client will
 * ever call its code. A manifest is metadata, not proof of safety; validation only prevents
 * malformed or incompatible declarations.
 */
public record ExtensionManifest(
        String id,
        String name,
        String version,
        int apiMajor,
        int apiMinor,
        String minClient,
        Set<String> capabilities,
        Set<String> dependencies,
        Set<String> optionalDependencies,
        String author,
        String license) {

    /** The {@code custom} key an extension manifest lives under. */
    public static final String CUSTOM_KEY = "logvex";

    public ExtensionManifest {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? id : name.trim();
        version = version == null ? "" : version.trim();
        minClient = minClient == null ? "" : minClient.trim();
        author = author == null ? "" : author.trim();
        license = license == null ? "" : license.trim();
        capabilities = Collections.unmodifiableSet(
                new LinkedHashSet<>(capabilities == null ? Set.of() : capabilities));
        dependencies = Collections.unmodifiableSet(
                new LinkedHashSet<>(dependencies == null ? Set.of() : dependencies));
        optionalDependencies = Collections.unmodifiableSet(
                new LinkedHashSet<>(optionalDependencies == null ? Set.of() : optionalDependencies));
    }

    /** A manifest with only the required fields; used when optional metadata is absent. */
    public static ExtensionManifest minimal(String id, String name, String version, int apiMajor, int apiMinor) {
        return new ExtensionManifest(id, name, version, apiMajor, apiMinor, "",
                Set.of(), Set.of(), Set.of(), "", "");
    }

    /** Thrown when a manifest cannot be accepted. The message is shown to the user. */
    public static final class InvalidManifestException extends RuntimeException {
        public InvalidManifestException(String message) {
            super(message);
        }
    }

    /**
     * Validates the manifest, throwing {@link InvalidManifestException} with a human-readable
     * reason when it cannot be accepted. Called before the extension's code is ever touched.
     */
    public void validate() {
        if (id.isEmpty()) {
            throw new InvalidManifestException("extension id must not be blank");
        }
        if (!id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new InvalidManifestException("extension id '" + id
                    + "' must be lower-case letters, digits, '-' or '_'");
        }
        if (version.isEmpty()) {
            throw new InvalidManifestException("extension '" + id + "' has no version");
        }
        if (apiMajor < 1) {
            throw new InvalidManifestException("extension '" + id + "' declares an invalid api major: " + apiMajor);
        }
        if (apiMinor < 0) {
            throw new InvalidManifestException("extension '" + id + "' declares an invalid api minor: " + apiMinor);
        }
        for (String capability : capabilities) {
            if (capability == null || capability.isBlank()) {
                throw new InvalidManifestException("extension '" + id + "' declares a blank capability");
            }
        }
    }

    /** True when this client's API can host the extension. */
    public boolean compatible() {
        return ApiVersion.supports(apiMajor, apiMinor);
    }

    /** A clear incompatibility message, or an empty string when compatible. */
    public String incompatibilityReason() {
        if (compatible()) {
            return "";
        }
        return "requires API " + apiMajor + "." + apiMinor + ", host provides " + ApiVersion.string();
    }

    /** A one-line description for the management UI and command surface. */
    public String summary() {
        return id + " v" + version + " (" + name + ") api " + apiMajor + "." + apiMinor
                + (capabilities.isEmpty() ? "" : " " + capabilities);
    }
}
