package com.logvex.crystal.ai.model;

import java.util.List;
import java.util.Optional;

/**
 * The curated catalogue of optional local models. Every entry is a small, CPU-friendly,
 * permissively-licensed GGUF instruction model. Nothing is bundled with the client; the user
 * chooses an entry and the client downloads exactly that file.
 *
 * <p>Sizes are the upstream published file sizes (rounded); they are re-checked against the
 * real download at fetch time. Each entry pins the SHA-256 verified against the upstream
 * release, so a download is checksum-verified before it is ever installed or loaded.
 */
public final class ModelCatalog {

    /**
     * Default entry: the smallest genuinely useful instruct model, ~0.36B parameters, Apache-2.0.
     * Chosen first because the spec prioritises low overhead over raw model intelligence.
     */
    public static final ModelSpec SMOLLM2_360M = new ModelSpec(
            "smollm2-360m-instruct-q4_k_m",
            "SmolLM2 360M Instruct",
            "Tiny Apache-2.0 instruct model. Best fit for the ~300 MB budget and CPU-only machines.",
            "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/"
                    + "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            "SmolLM2-360M-Instruct-Q4_K_M.gguf",
            270_590_880L,
            "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2",
            "Apache-2.0",
            "Q4_K_M",
            "0.36B",
            2048);

    /** A slightly larger option when the user wants better explanations and has the RAM. */
    public static final ModelSpec QWEN25_05B = new ModelSpec(
            "qwen2.5-0.5b-instruct-q4_k_m",
            "Qwen2.5 0.5B Instruct",
            "Apache-2.0 0.5B instruct model. A little larger; stronger explanations, still CPU-friendly.",
            "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/"
                    + "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            397_808_192L,
            "6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653",
            "Apache-2.0",
            "Q4_K_M",
            "0.5B",
            32768);

    private static final List<ModelSpec> ALL = List.of(SMOLLM2_360M, QWEN25_05B);

    private ModelCatalog() {
    }

    public static List<ModelSpec> all() {
        return ALL;
    }

    public static Optional<ModelSpec> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return ALL.stream().filter(spec -> spec.id().equals(id)).findFirst();
    }

    /** The default entry id, used when a stored setting names an unknown model. */
    public static String defaultId() {
        return SMOLLM2_360M.id();
    }
}
