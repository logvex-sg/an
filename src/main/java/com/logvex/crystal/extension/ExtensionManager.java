package com.logvex.crystal.extension;

import com.logvex.crystal.api.ClientApi;
import com.logvex.crystal.api.LogvexExtension;
import com.logvex.crystal.extension.ExtensionManifest.InvalidManifestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The extension lifecycle: discovery, validation, activation, deactivation and cleanup.
 *
 * <p>Design decisions, made explicit:
 * <ul>
 *   <li><b>Compile-time integration, not a runtime classloader.</b> Extensions are ordinary
 *       Fabric mods discovered through the loader. There is no custom classloading and no
 *       sandbox, because a Java classloader is not one. The honest consequence - code is never
 *       unloaded, only its registrations are released - is documented in
 *       {@code docs/extension-security.md}.</li>
 *   <li><b>Discovery never runs code.</b> Scanning reads manifests and holds lazy loaders; the
 *       entry point is only created when the user enables the extension.</li>
 *   <li><b>Explicit user approval.</b> {@link #enable(String)} is the only path to activation;
 *       nothing is enabled automatically.</li>
 *   <li><b>Rollback on failure.</b> If {@code initialize} throws, every registration made before
 *       the throw is released and the extension is marked {@code FAILED}, never {@code ACTIVE}.</li>
 * </ul>
 *
 * <p>Minecraft-independent: the host is an {@link ExtensionHost} and the logger is injected, so
 * the whole lifecycle is unit tested with a fake host.
 */
public final class ExtensionManager {

    /** A sink for lifecycle messages, kept small so it is easy to fake in tests. */
    public interface Log {
        void warn(String message);

        void error(String message, Throwable error);
    }

    private final ExtensionHost host;
    private final Log log;
    private final ExtensionPreferences preferences;
    private final Map<String, ExtensionState> states = new LinkedHashMap<>();
    private final Map<String, ExtensionCandidate> candidates = new LinkedHashMap<>();
    private final Map<String, LogvexExtension> active = new LinkedHashMap<>();
    private int failures;

    public ExtensionManager(ExtensionHost host, Log log) {
        this(host, log, null);
    }

    public ExtensionManager(ExtensionHost host, Log log, ExtensionPreferences preferences) {
        this.host = host;
        this.log = log;
        this.preferences = preferences;
    }

    /**
     * Registers a discovered candidate. Validates the manifest and records the outcome as a
     * state; never loads the extension's code. Returns the recorded state.
     */
    public ExtensionState discover(ExtensionCandidate candidate) {
        ExtensionManifest manifest = candidate.manifest();
        try {
            manifest.validate();
        } catch (InvalidManifestException e) {
            ExtensionState state = new ExtensionState(manifest, candidate.modId(),
                    ExtensionState.Status.INVALID, e.getMessage());
            states.put(keyFor(candidate), state);
            log.warn("Extension rejected: " + e.getMessage());
            return state;
        }
        String key = keyFor(candidate);
        if (states.containsKey(key)) {
            ExtensionState existing = states.get(key);
            log.warn("Duplicate extension id '" + key + "' from mod '" + candidate.modId()
                    + "' ignored; already provided by '" + existing.modId() + "'");
            return existing;
        }
        candidates.put(key, candidate);
        ExtensionState.Status status = manifest.compatible()
                ? ExtensionState.Status.AVAILABLE : ExtensionState.Status.INCOMPATIBLE;
        String detail = manifest.compatible() ? "" : manifest.incompatibilityReason();
        ExtensionState state = new ExtensionState(manifest, candidate.modId(), status, detail);
        states.put(key, state);
        return state;
    }

    /**
     * Activates an extension by id. Requires that it was discovered, is compatible and has not
     * failed. Idempotent: enabling an already-active extension returns true without reloading.
     */
    public boolean enable(String id) {
        String key = normalize(id);
        ExtensionState state = states.get(key);
        if (state == null) {
            log.warn("No such extension: " + id);
            return false;
        }
        if (state.status() == ExtensionState.Status.ACTIVE) {
            return true;
        }
        if (!state.enableable()) {
            log.warn("Extension '" + id + "' cannot be enabled: " + state.status());
            return false;
        }
        ExtensionCandidate candidate = candidates.get(key);
        if (candidate == null) {
            state.set(ExtensionState.Status.FAILED, "no candidate to load");
            return false;
        }
        ClientApi api = host.open(key);
        LogvexExtension extension;
        try {
            extension = candidate.loader().get();
            if (extension == null) {
                throw new IllegalStateException("entrypoint returned null");
            }
            extension.initialize(api);
        } catch (Throwable t) {
            // Roll back everything the extension managed to register before it threw.
            host.close(key);
            state.set(ExtensionState.Status.FAILED, describe(t));
            failures++;
            log.error("Extension '" + id + "' failed to initialize", t);
            return false;
        }
        active.put(key, extension);
        state.set(ExtensionState.Status.ACTIVE, "");
        approve(key);
        return true;
    }

    /**
     * Enables every discovered extension the user approved in a previous session. Called once
     * at startup. Never enables an extension that was not explicitly approved before.
     */
    public int enableApproved() {
        if (preferences == null) {
            return 0;
        }
        int enabledCount = 0;
        for (String key : new ArrayList<>(states.keySet())) {
            ExtensionState state = states.get(key);
            if (state != null && state.enableable() && preferences.isApproved(key) && enable(key)) {
                enabledCount++;
            }
        }
        return enabledCount;
    }

    private void approve(String key) {
        if (preferences == null) {
            return;
        }
        if (preferences.approve(key)) {
            try {
                preferences.save();
            } catch (IOException e) {
                log.error("Could not persist extension approval for '" + key + "'", e);
            }
        }
    }

    private void revoke(String key) {
        if (preferences == null) {
            return;
        }
        if (preferences.revoke(key)) {
            try {
                preferences.save();
            } catch (IOException e) {
                log.error("Could not persist extension revocation for '" + key + "'", e);
            }
        }
    }

    /**
     * Deactivates an extension by id: releases its registrations, then calls its shutdown hook.
     * The classes stay loaded (see the class comment); only the registrations are removed.
     */
    public boolean disable(String id) {
        String key = normalize(id);
        ExtensionState state = states.get(key);
        if (state == null || state.status() != ExtensionState.Status.ACTIVE) {
            return false;
        }
        host.close(key);
        LogvexExtension extension = active.remove(key);
        if (extension != null) {
            try {
                extension.shutdown();
            } catch (Throwable t) {
                log.error("Extension '" + id + "' threw during shutdown", t);
            }
        }
        state.set(ExtensionState.Status.DISABLED, "");
        revoke(key);
        return true;
    }

    /** The installed extensions, in discovery order. */
    public List<ExtensionState> installed() {
        return new ArrayList<>(states.values());
    }

    public Optional<ExtensionState> state(String id) {
        return Optional.ofNullable(states.get(normalize(id)));
    }

    public boolean isEnabled(String id) {
        ExtensionState state = states.get(normalize(id));
        return state != null && state.status() == ExtensionState.Status.ACTIVE;
    }

    public int activeCount() {
        return active.size();
    }

    public int failureCount() {
        return failures;
    }

    /** Removes all state. Used when rescanning; disables active extensions first. */
    public void reload() {
        for (String key : new ArrayList<>(active.keySet())) {
            disable(key);
        }
        states.clear();
        candidates.clear();
    }

    /** Deactivates every active extension. Called at client shutdown. */
    public void shutdownAll() {
        for (String key : new ArrayList<>(active.keySet())) {
            disable(key);
        }
    }

    /** A one-line summary for diagnostics. */
    public String summary() {
        long activeCount = states.values().stream().filter(ExtensionState::loaded).count();
        return "extensions: " + states.size() + " installed, " + activeCount + " active, "
                + failures + " failure(s)";
    }

    private static String keyFor(ExtensionCandidate candidate) {
        return normalize(candidate.manifest().id());
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
