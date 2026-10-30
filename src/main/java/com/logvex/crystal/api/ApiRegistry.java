package com.logvex.crystal.api;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.EventKind;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Owns every registration made through {@link ClientApi}, grouped by the owner that made it.
 *
 * <p>The rule this enforces is the Phase 1.3 contract: <em>every registration has an owner and a
 * cleanup</em>. When an owner opens a scope it receives a {@link ClientApi}; each call on it
 * returns an {@link ApiHandle} that the scope remembers. Closing the scope - or closing the
 * whole registry at shutdown - releases every handle in reverse order, so no listener, command,
 * module or HUD element can outlive its owner.
 *
 * <p>All the work is delegated to an injected {@link ApiServices}, so the ownership and rollback
 * rules are unit tested with a recording fake and no Minecraft runtime.
 */
public final class ApiRegistry {

    private final ApiServices services;
    private final Supplier<Capabilities> capabilities;
    private final String clientVersion;

    private final Map<String, Scope> scopes = new LinkedHashMap<>();

    public ApiRegistry(ApiServices services, Supplier<Capabilities> capabilities, String clientVersion) {
        this.services = services;
        this.capabilities = capabilities;
        this.clientVersion = clientVersion;
    }

    /** An {@link com.logvex.crystal.extension.ExtensionHost} view of this registry. */
    public com.logvex.crystal.extension.ExtensionHost asExtensionHost() {
        return new com.logvex.crystal.extension.ExtensionHost() {
            @Override
            public ClientApi open(String owner) {
                return ApiRegistry.this.open(owner);
            }

            @Override
            public boolean close(String owner) {
                return ApiRegistry.this.close(owner);
            }
        };
    }

    /**
     * Opens a scope for {@code owner}. A second open with the same owner returns the existing
     * scope, so a re-enable does not leak the first set of registrations.
     */
    public ClientApi open(String owner) {
        Scope existing = scopes.get(owner);
        if (existing != null) {
            return existing;
        }
        Scope scope = new Scope(owner);
        scopes.put(owner, scope);
        return scope;
    }

    /** Releases every registration owned by {@code owner}. Returns true if a scope was closed. */
    public boolean close(String owner) {
        Scope scope = scopes.remove(owner);
        if (scope == null) {
            return false;
        }
        scope.releaseAll();
        return true;
    }

    /** Releases everything. Called at client shutdown. */
    public void closeAll() {
        List<Scope> all = new ArrayList<>(scopes.values());
        scopes.clear();
        for (Scope scope : all) {
            scope.releaseAll();
        }
    }

    /** The owners with an open scope, for diagnostics. */
    public Set<String> owners() {
        return Set.copyOf(scopes.keySet());
    }

    /** Total live registrations across all scopes. */
    public int registrationCount() {
        return scopes.values().stream().mapToInt(scope -> scope.handles.size()).sum();
    }

    /** A per-owner registration count, for diagnostics. */
    public Map<String, Integer> registrationCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        scopes.forEach((owner, scope) -> result.put(owner, scope.handles.size()));
        return result;
    }

    private final class Scope implements ClientApi {

        private final String owner;
        private final List<ApiHandle> handles = new ArrayList<>();

        Scope(String owner) {
            this.owner = owner;
        }

        void releaseAll() {
            // Reverse order so a registration that depends on an earlier one tears down first.
            for (int i = handles.size() - 1; i >= 0; i--) {
                try {
                    handles.get(i).close();
                } catch (RuntimeException ignored) {
                    // A broken handle must not block the rest of the cleanup.
                }
            }
            handles.clear();
        }

        private ApiHandle track(String description, AutoCloseable registration) {
            ApiHandle handle = new SimpleHandle(description, registration);
            handles.add(handle);
            return handle;
        }

        private ApiHandle failed(String description, String reason) {
            services.recordError(owner, description + " failed: " + reason);
            return new SimpleHandle(description, null);
        }

        @Override
        public String apiVersion() {
            return ApiVersion.string();
        }

        @Override
        public Capabilities capabilities() {
            return ApiRegistry.this.capabilities.get();
        }

        @Override
        public String clientVersion() {
            return clientVersion;
        }

        @Override
        public ApiHandle subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener) {
            String description = "events:" + kinds;
            try {
                return track(description, services.subscribe(kinds, listener));
            } catch (RuntimeException e) {
                return failed(description, e.getMessage());
            }
        }

        @Override
        public ApiHandle registerModule(Module module) {
            String description = "module:" + module.name();
            try {
                return track(description, services.registerModule(module));
            } catch (RuntimeException e) {
                return failed(description, e.getMessage());
            }
        }

        @Override
        public ApiHandle registerCommand(String name, Predicate<List<String>> handler) {
            String description = "command:" + name;
            try {
                return track(description, services.registerCommand(name, handler));
            } catch (RuntimeException e) {
                return failed(description, e.getMessage());
            }
        }

        @Override
        public ApiHandle registerHudElement(HudElement element) {
            String description = "hud:" + element.id();
            try {
                return track(description, services.registerHudElement(element));
            } catch (RuntimeException e) {
                return failed(description, e.getMessage());
            }
        }

        @Override
        public void notify(String message, String level) {
            services.notify(message, parseLevel(level));
        }

        @Override
        public void log(String message) {
            services.recordError(owner, message);
        }

        private NotificationLevel parseLevel(String level) {
            if (level == null) {
                return NotificationLevel.INFO;
            }
            return switch (level.toLowerCase(Locale.ROOT)) {
                case "success" -> NotificationLevel.SUCCESS;
                case "warn", "warning" -> NotificationLevel.WARN;
                case "error" -> NotificationLevel.ERROR;
                case "cute" -> NotificationLevel.CUTE;
                default -> NotificationLevel.INFO;
            };
        }
    }

    /** A handle backed by a close action; idempotent. A null action is an inert, closed handle. */
    private static final class SimpleHandle implements ApiHandle {
        private final String description;
        private AutoCloseable closeAction;
        private boolean closed;

        SimpleHandle(String description, AutoCloseable closeAction) {
            this.description = description;
            this.closeAction = closeAction;
            this.closed = closeAction == null;
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public boolean active() {
            return !closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            AutoCloseable action = closeAction;
            closeAction = null;
            if (action != null) {
                try {
                    action.close();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to release " + description, e);
                }
            }
        }
    }
}
