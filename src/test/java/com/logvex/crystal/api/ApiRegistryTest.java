package com.logvex.crystal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.EventKind;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.junit.jupiter.api.Test;

/**
 * Covers the ownership and cleanup contract: every registration is released exactly once when
 * its owner scope closes, and a rejected registration is reported rather than left dangling.
 * Uses a recording fake {@link ApiServices} so no game runtime is needed.
 */
class ApiRegistryTest {

    /** Records every registration and whether it was released. */
    private static final class RecordingServices implements ApiServices {
        final List<String> registered = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<NotificationLevel> notifications = new ArrayList<>();
        boolean rejectCommands;

        private AutoCloseable closer(String name) {
            registered.add(name);
            return () -> released.add(name);
        }

        @Override
        public AutoCloseable subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener) {
            return closer("subscribe");
        }

        @Override
        public AutoCloseable registerModule(Module module) {
            return closer("module:" + module.name());
        }

        @Override
        public AutoCloseable registerCommand(String name, Predicate<List<String>> handler) {
            if (rejectCommands) {
                throw new IllegalArgumentException("name taken: " + name);
            }
            return closer("command:" + name);
        }

        @Override
        public AutoCloseable registerHudElement(HudElement element) {
            return closer("hud:" + element.id());
        }

        @Override
        public void notify(String message, NotificationLevel level) {
            notifications.add(level);
        }

        @Override
        public void recordError(String owner, String message) {
            errors.add(owner + ": " + message);
        }
    }

    private static final class StubModule extends Module {
        StubModule(String name) {
            super(Category.MISC, name, "");
        }
    }

    private static final class StubHud implements HudElement {
        @Override
        public String id() {
            return "test-hud";
        }

        @Override
        public Column column() {
            return Column.LEFT;
        }

        @Override
        public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y,
                           boolean rightAligned) {
        }

        @Override
        public int height(MinecraftClient client) {
            return 0;
        }
    }

    private static ApiRegistry newRegistry(RecordingServices services) {
        return new ApiRegistry(services,
                () -> new Capabilities(Set.of(Capabilities.EVENTS, Capabilities.HUD)),
                "1.0.0-test");
    }

    @Test
    void closingAScopeReleasesEveryRegistrationOnce() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        ClientApi api = registry.open("owner-a");

        api.subscribe(Set.of(EventKind.TARGET_CHANGED), event -> { });
        api.registerModule(new StubModule("Demo"));
        api.registerCommand("demo", args -> true);
        api.registerHudElement(new StubHud());
        assertEquals(4, registry.registrationCount());

        assertTrue(registry.close("owner-a"));
        assertEquals(List.of("hud:test-hud", "command:demo", "module:Demo", "subscribe"),
                services.released, "released in reverse registration order");
        assertEquals(0, registry.registrationCount());
    }

    @Test
    void reopeningAnOwnerReturnsTheSameScope() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        ClientApi first = registry.open("owner-a");
        ClientApi second = registry.open("owner-a");
        assertTrue(first == second, "a second open must not create a second scope");
        assertEquals(1, registry.owners().size());
    }

    @Test
    void rejectedRegistrationIsReportedAndNotTracked() {
        RecordingServices services = new RecordingServices();
        services.rejectCommands = true;
        ApiRegistry registry = newRegistry(services);
        ClientApi api = registry.open("owner-a");

        ApiHandle handle = api.registerCommand("taken", args -> true);
        assertFalse(handle.active(), "a rejected registration yields an inert handle");
        assertEquals(0, registry.registrationCount());
        assertTrue(services.errors.stream().anyMatch(message -> message.contains("command:taken")),
                "the rejection is recorded for diagnostics");
    }

    @Test
    void closeAllReleasesEveryScope() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        registry.open("a").registerCommand("one", args -> true);
        registry.open("b").registerCommand("two", args -> true);
        assertEquals(2, registry.registrationCount());

        registry.closeAll();
        assertEquals(0, registry.registrationCount());
        assertTrue(registry.owners().isEmpty());
        assertEquals(2, services.released.size());
    }

    @Test
    void closingAnUnknownOwnerIsHarmless() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        assertFalse(registry.close("nobody"));
    }

    @Test
    void scopeExposesVersionAndCapabilities() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        ClientApi api = registry.open("owner-a");
        assertEquals(ApiVersion.string(), api.apiVersion());
        assertTrue(api.capabilities().has(Capabilities.HUD));
        assertFalse(api.capabilities().has(Capabilities.BARITONE));
        assertEquals("1.0.0-test", api.clientVersion());
    }

    @Test
    void notifyMapsLevelNames() {
        RecordingServices services = new RecordingServices();
        ApiRegistry registry = newRegistry(services);
        ClientApi api = registry.open("owner-a");
        api.notify("hi", "cute");
        api.notify("oops", "error");
        api.notify("plain", null);
        assertEquals(List.of(NotificationLevel.CUTE, NotificationLevel.ERROR, NotificationLevel.INFO),
                services.notifications);
    }
}
