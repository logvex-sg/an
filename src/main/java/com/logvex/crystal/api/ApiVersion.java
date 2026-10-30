package com.logvex.crystal.api;

/**
 * The version of the stable extension API.
 *
 * <p>Two numbers matter to an extension author: the API <em>major</em> (a change that can
 * break a compiled extension) and the API <em>minor</em> (additive changes). An extension
 * declares the API range it supports in its manifest; {@link #supports(int, int)} answers
 * whether this client can host it.
 *
 * <p>The version is deliberately hand-maintained rather than derived from the mod version, so
 * a cosmetic client release does not silently change the API contract. Bump {@link #MAJOR}
 * only for a breaking change, {@link #MINOR} for additive ones.
 */
public final class ApiVersion {

    /** Breaking changes bump this. */
    public static final int MAJOR = 1;
    /** Backward-compatible additions bump this. */
    public static final int MINOR = 0;

    private ApiVersion() {
    }

    /** The API version string, e.g. {@code 1.0}. */
    public static String string() {
        return MAJOR + "." + MINOR;
    }

    /**
     * True when a host offering {@code hostMajor.hostMinor} satisfies an extension that
     * requires {@code requiredMajor} and tolerates up to {@code requiredMinor} within it.
     *
     * <p>Rule: the major must match exactly, and the host minor must be at least the required
     * minor. A host minor above the requirement is fine (additive changes never remove a
     * symbol), which is why this is a one-sided check.
     */
    public static boolean isCompatible(int hostMajor, int hostMinor, int requiredMajor, int requiredMinor) {
        return hostMajor == requiredMajor && hostMinor >= requiredMinor;
    }

    /** True when this client's API can host an extension requiring the given version. */
    public static boolean supports(int requiredMajor, int requiredMinor) {
        return isCompatible(MAJOR, MINOR, requiredMajor, requiredMinor);
    }
}
