package com.logvex.crystal.ai;

import com.logvex.crystal.ai.model.InferenceException;
import com.logvex.crystal.ai.model.InferenceExecutor;
import com.logvex.crystal.ai.model.MiniAIModel;
import com.logvex.crystal.ai.model.ModelCatalog;
import com.logvex.crystal.ai.model.ModelLoader;
import com.logvex.crystal.ai.model.ModelSpec;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns the optional local model: lazy loading, a single copy, idle unloading, and the one
 * bounded executor all inference runs on. It is the only thing that decides when the model
 * loads, so "never during startup, never on the game thread, never two copies" are enforced
 * in exactly one place.
 *
 * <p>The manager is Minecraft-independent (it takes a directory and the live settings) so the
 * whole lifecycle can be unit tested with a fake model. Nothing here blocks a caller: submitting
 * work returns immediately, and a full queue drops the newest request instead of waiting.
 */
public final class MiniAIManager implements AutoCloseable {

    /** Coarse model lifecycle state, surfaced to the UI. */
    public enum State {
        DISABLED("Disabled"),
        NOT_INSTALLED("Model not installed"),
        IDLE("Model unloaded"),
        LOADING("Loading model\u2026"),
        READY("Ready"),
        RUNTIME_UNAVAILABLE("No local runtime"),
        FAILED("Failed to load");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Path modelDirectory;
    private final AISettings settings;
    private final ModelLoader loader;
    /** Injectable for tests; production resolves a real runtime (usually unavailable). */
    private final Loader loaderHook;
    /** Resolves the active spec; overridable so tests can use a tiny stand-in model. */
    private final java.util.function.Supplier<ModelSpec> specSupplier;

    /** Strategy used to obtain a model instance from an installed file. */
    public interface Loader {
        MiniAIModel load(ModelSpec spec, int threads) throws InferenceException;
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicReference<MiniAIModel> model = new AtomicReference<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Future<?>> loadFuture = new AtomicReference<>();
    private final AtomicReference<Future<?>> inFlightFuture = new AtomicReference<>();

    private volatile InferenceExecutor executor;
    private volatile long lastUseMillis;
    private volatile long lastLatencyMillis = -1;
    private volatile String lastError = "";
    private volatile boolean loadInProgress;

    public MiniAIManager(Path modelDirectory, AISettings settings) {
        this(modelDirectory, settings, null, null);
    }

    public MiniAIManager(Path modelDirectory, AISettings settings, Loader loaderHook) {
        this(modelDirectory, settings, loaderHook, null);
    }

    public MiniAIManager(Path modelDirectory, AISettings settings, Loader loaderHook,
                         java.util.function.Supplier<ModelSpec> specSupplier) {
        this.modelDirectory = modelDirectory;
        this.settings = settings;
        this.loader = new ModelLoader(modelDirectory);
        this.loaderHook = loaderHook != null ? loaderHook : loader::load;
        this.specSupplier = specSupplier != null ? specSupplier
                : () -> ModelCatalog.byId(settings.modelId).orElse(ModelCatalog.all().get(0));
        reconfigure();
    }

    public Path modelDirectory() {
        return modelDirectory;
    }

    public ModelLoader loader() {
        return loader;
    }

    public State state() {
        return state.get();
    }

    public String lastError() {
        return lastError;
    }

    public long lastLatencyMillis() {
        return lastLatencyMillis;
    }

    public long loadedBytes() {
        MiniAIModel m = model.get();
        return m == null ? -1 : m.loadedBytes();
    }

    public boolean isLoaded() {
        MiniAIModel m = model.get();
        return m != null && m.isLoaded();
    }

    public ModelSpec currentSpec() {
        return specSupplier.get();
    }

    /** Re-reads settings: rebuild the executor when enablement or limits changed. */
    public void reconfigure() {
        settings.clamp();
        if (!settings.enabled) {
            closeExecutor();
            state.set(State.DISABLED);
            return;
        }
        int queue = settings.effectiveQueueLimit();
        if (executor == null || executor.queueLimit() != queue) {
            closeExecutor();
            executor = new InferenceExecutor(settings.inferenceThreads, queue, "logvex-ai");
        }
        if (state.get() == State.DISABLED) {
            state.set(isLoaded() ? State.READY : State.IDLE);
        }
    }

    /**
     * Ensures the model is loaded, on the executor, at most once. Safe to call from the game
     * thread; it only schedules work. No-op when AI is disabled or the model is not installed.
     */
    public void ensureLoaded() {
        if (!settings.enabled) {
            state.set(State.DISABLED);
            return;
        }
        if (isLoaded() || loadInProgress) {
            return;
        }
        ModelSpec spec = currentSpec();
        if (!loader.isInstalled(spec)) {
            state.set(State.NOT_INSTALLED);
            return;
        }
        InferenceExecutor exec = executor;
        if (exec == null) {
            reconfigure();
            exec = executor;
        }
        if (exec == null) {
            state.set(State.DISABLED);
            return;
        }
        loadInProgress = true;
        state.set(State.LOADING);
        Future<?> future = exec.submit(() -> {
            try {
                MiniAIModel loaded = loaderHook.load(spec, settings.inferenceThreads);
                model.set(loaded);
                state.set(State.READY);
                lastError = "";
            } catch (InferenceException e) {
                lastError = e.getMessage() == null ? "load failed" : e.getMessage();
                state.set(lastError.toLowerCase().contains("runtime") ? State.RUNTIME_UNAVAILABLE : State.FAILED);
            } catch (Throwable t) {
                lastError = String.valueOf(t.getMessage());
                state.set(State.FAILED);
            } finally {
                loadInProgress = false;
            }
            return null;
        });
        loadFuture.set(future);
        if (future == null) {
            // Queue was full; let a later request retry rather than wedging in LOADING.
            loadInProgress = false;
            state.set(State.IDLE);
        }
    }

    /**
     * Submits {@code prompt} for inference exactly once per {@code key} while an identical
     * request is still queued or running. {@code onResult} runs on the AI worker thread and
     * receives the model text, or {@code null} when the request was dropped or failed.
     *
     * @return the future to cancel, or null when nothing was scheduled
     */
    public Future<?> submitOnce(String key, String prompt, Consumer<String> onResult) {
        if (!settings.enabled) {
            return null;
        }
        ensureLoaded();
        if (state.get() != State.READY) {
            return null;
        }
        InferenceExecutor exec = executor;
        if (exec == null) {
            return null;
        }
        if (key != null && !inFlight.add(key)) {
            return null; // identical request already pending
        }
        lastUseMillis = System.currentTimeMillis();
        Future<?> future = exec.submit(() -> {
            long start = System.nanoTime();
            String result = null;
            try {
                MiniAIModel m = model.get();
                if (m != null && m.isLoaded()) {
                    result = m.complete(prompt, 256, 0.2f);
                }
            } catch (InferenceException e) {
                lastError = e.getMessage();
            } catch (Throwable t) {
                lastError = String.valueOf(t.getMessage());
            } finally {
                lastLatencyMillis = (System.nanoTime() - start) / 1_000_000L;
                if (key != null) {
                    inFlight.remove(key);
                }
                if (onResult != null) {
                    try {
                        onResult.accept(result);
                    } catch (RuntimeException ignored) {
                        // A consumer must never break the worker.
                    }
                }
            }
            return null;
        });
        if (future == null) {
            if (key != null) {
                inFlight.remove(key);
            }
            return null;
        }
        inFlightFuture.set(future);
        return future;
    }

    /** Cancels the current request (used when a newer one supersedes it). */
    public void cancelInFlight() {
        Future<?> future = inFlightFuture.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    /** Unloads the model, releasing memory. Safe to call from any thread. */
    public void unload() {
        InferenceExecutor exec = executor;
        MiniAIModel m = model.getAndSet(null);
        Runnable task = () -> {
            if (m != null) {
                try {
                    m.unload();
                } catch (Throwable ignored) {
                    // Unloading is best-effort; dropping the reference is enough.
                }
            }
        };
        if (exec != null && !exec.isIdle()) {
            exec.submit(() -> {
                task.run();
                return null;
            });
        } else {
            task.run();
        }
        if (settings.enabled) {
            state.set(loader.isInstalled(currentSpec()) ? State.IDLE : State.NOT_INSTALLED);
        }
    }

    /** Idle-unloads the model after the configured period. Call from the client tick. */
    public void tick(long nowMillis) {
        if (!settings.enabled || !isLoaded()) {
            return;
        }
        long idleMillis = (long) settings.idleUnloadMinutes * 60_000L;
        if (lastUseMillis > 0 && nowMillis - lastUseMillis > idleMillis) {
            InferenceExecutor exec = executor;
            if (exec == null || exec.isIdle()) {
                unload();
            }
        }
    }

    public int queueDepth() {
        InferenceExecutor exec = executor;
        return exec == null ? 0 : exec.queueDepth();
    }

    public int activeCount() {
        InferenceExecutor exec = executor;
        return exec == null ? 0 : exec.activeCount();
    }

    public int rejectedCount() {
        InferenceExecutor exec = executor;
        return exec == null ? 0 : exec.rejectedCount();
    }

    public int completedCount() {
        InferenceExecutor exec = executor;
        return exec == null ? 0 : exec.completedCount();
    }

    public int queueLimit() {
        InferenceExecutor exec = executor;
        return exec == null ? 0 : exec.queueLimit();
    }

    private void closeExecutor() {
        InferenceExecutor exec = executor;
        executor = null;
        if (exec != null) {
            exec.close();
        }
    }

    @Override
    public void close() {
        cancelInFlight();
        unload();
        closeExecutor();
    }
}
