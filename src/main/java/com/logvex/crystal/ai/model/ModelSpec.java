package com.logvex.crystal.ai.model;

/**
 * A small local model the client knows how to fetch, verify and run. The catalogue is data,
 * not code: the user picks an entry, the client downloads exactly the listed file and checks
 * the recorded SHA-256 before it is ever loaded.
 *
 * <p>Only gguf-format instruction models with a permissive licence and CPU inference are
 * listed. {@code sha256} may be blank for an entry that has not been pinned yet, in which case
 * the download is size-checked only and the UI says so.
 */
public record ModelSpec(
        String id,
        String displayName,
        String description,
        String url,
        String fileName,
        long sizeBytes,
        String sha256,
        String license,
        String quantization,
        String parameterCount,
        int contextTokens) {

    public double sizeMegabytes() {
        return sizeBytes / (1024.0 * 1024.0);
    }

    /** True when a pinned hash is available so the download can be verified cryptographically. */
    public boolean hasPinnedHash() {
        return sha256 != null && sha256.length() == 64;
    }
}
