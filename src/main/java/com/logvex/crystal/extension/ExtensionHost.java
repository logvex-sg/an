package com.logvex.crystal.extension;

import com.logvex.crystal.api.ClientApi;

/**
 * The seam between the extension manager and the client's registration registry.
 *
 * <p>{@link com.logvex.crystal.api.ApiRegistry} implements this. Keeping it an interface lets
 * the extension lifecycle (discovery, validation, activation, rollback, cleanup) be unit tested
 * with a fake host, without standing up the whole client.
 */
public interface ExtensionHost {

    /** Opens (or reuses) a registration scope for the given owner. */
    ClientApi open(String owner);

    /** Releases every registration owned by the given owner. */
    boolean close(String owner);
}
