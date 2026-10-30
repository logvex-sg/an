package com.logvex.crystal.ai.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Optional bridge to a local GGUF inference runtime, resolved <em>by name</em>.
 *
 * <p>The client bundles no native LLM binding, so on a stock install this finds nothing and
 * returns {@code null}; {@link ModelLoader#load} then reports the runtime as unavailable and the
 * rules-based engine takes over. If a compatible runtime (e.g. a {@code de.kherud.llama}-style
 * binding) is present on the classpath, this wraps it reflectively.
 *
 * <p>It never loads arbitrary user-supplied classes: only two well-known, fixed class names are
 * probed. Any reflection failure degrades to "unavailable" rather than propagating.
 */
final class LlamaRuntimeBinding {

    /** Candidate runtime class names, most-preferred first. */
    private static final String[] CANDIDATES = {
            "de.kherud.llama.LlamaModel",
            "org.llama.LlamaModel",
    };

    private LlamaRuntimeBinding() {
    }

    /** Returns a loaded model, or null when no supported runtime is present. Never throws. */
    static MiniAIModel tryCreate(Path modelFile, int threads) {
        for (String className : CANDIDATES) {
            try {
                Class<?> type = Class.forName(className, false, LlamaRuntimeBinding.class.getClassLoader());
                MiniAIModel model = instantiate(type, modelFile, threads);
                if (model != null) {
                    return model;
                }
            } catch (Throwable ignored) {
                // Probe the next candidate; a missing/broken runtime is expected and not an error.
            }
        }
        return null;
    }

    private static MiniAIModel instantiate(Class<?> type, Path modelFile, int threads) {
        try {
            // The kherud binding exposes a (ModelParameters) or (ModelParameters, ...) constructor.
            Object parameters = buildParameters(type, modelFile, threads);
            if (parameters == null) {
                return null;
            }
            Class<?> paramsType = parameters.getClass();
            Constructor<?> ctor = type.getConstructor(paramsType);
            Object instance = ctor.newInstance(parameters);
            return new ReflectiveModel(type, instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object buildParameters(Class<?> modelType, Path modelFile, int threads) {
        try {
            Class<?> paramsType = Class.forName("de.kherud.llama.ModelParameters");
            Object params = paramsType.getConstructor().newInstance();
            invoke(params, "setModelFilePath", String.class, modelFile.toAbsolutePath().toString());
            invoke(params, "setThreads", int.class, threads);
            return params;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invoke(Object target, String method, Class<?> argType, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, argType);
            m.invoke(target, arg);
        } catch (Throwable ignored) {
            // Parameter is optional; a runtime without it still works with defaults.
        }
    }

    /** A {@link MiniAIModel} backed by a reflectively-loaded runtime instance. */
    private static final class ReflectiveModel implements MiniAIModel {
        private final Class<?> type;
        private Object instance;
        private boolean loaded = true;

        ReflectiveModel(Class<?> type, Object instance) {
            this.type = type;
            this.instance = instance;
        }

        @Override
        public String name() {
            return type.getSimpleName();
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public long loadedBytes() {
            return -1;
        }

        @Override
        public String complete(String prompt, int maxTokens, float temperature) throws InferenceException {
            if (!loaded) {
                throw new InferenceException("Model is unloaded");
            }
            try {
                Method chat = type.getMethod("complete", String.class);
                Object result = chat.invoke(instance, prompt);
                return result == null ? "" : result.toString();
            } catch (Throwable t) {
                throw new InferenceException("Inference failed: " + t.getMessage(), t);
            }
        }

        @Override
        public void unload() {
            if (!loaded) {
                return;
            }
            loaded = false;
            try {
                Method close = type.getMethod("close");
                close.invoke(instance);
            } catch (Throwable ignored) {
                // Nothing else to release; dropping the reference frees the model.
            }
            instance = null;
        }
    }
}
