package com.logvex.crystal.extension;

import com.logvex.crystal.api.LogvexExtension;
import java.util.function.Supplier;

/**
 * A discovered extension: its manifest, the Fabric mod id it came from, and a loader that
 * produces its entry point. The loader is only invoked when the user enables the extension,
 * never during discovery - discovering a mod must not run its code.
 *
 * @param modId    the Fabric mod id the manifest was found in
 * @param manifest the validated (or to-be-validated) manifest
 * @param loader   produces the entry point; may throw if the entrypoint is missing
 */
public record ExtensionCandidate(String modId, ExtensionManifest manifest,
                                 Supplier<LogvexExtension> loader) {
}
