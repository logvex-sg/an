package com.logvex.crystal.ai.model;

/**
 * The client's view of one local model. Implementations must be usable from the AI worker
 * thread only, and must release native memory in {@link #unload()}.
 *
 * <p>The interface exists so the manager can be driven in tests by a fake model, and so the
 * runtime (a GGUF/llama binding) stays swappable without touching the rest of the subsystem.
 */
public interface MiniAIModel {

    /** A short identifier for logs and the UI. */
    String name();

    /** True once the model is ready to answer. */
    boolean isLoaded();

    /** Approximate resident memory of the loaded model in bytes, or -1 when unknown. */
    long loadedBytes();

    /**
     * Generates a completion for {@code prompt}. Must be blocking and must honour the thread's
     * interrupt flag (so the executor can cancel an obsolete request).
     *
     * @param maxTokens upper bound on generated tokens
     * @param temperature sampling temperature (0 for greedy)
     * @return the model's text, possibly empty; never null
     * @throws InferenceException when the runtime fails
     */
    String complete(String prompt, int maxTokens, float temperature) throws InferenceException;

    /** Releases the model and any native memory. Idempotent. */
    void unload();
}
