package com.logvex.crystal.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.api.ApiHandle;
import com.logvex.crystal.api.ClientApi;
import com.logvex.crystal.api.Capabilities;
import com.logvex.crystal.api.LogvexExtension;
import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.EventKind;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Module;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * Covers the extension lifecycle: validation gating, explicit activation, rollback on failure,
 * cleanup on disable, and the "never auto-run an unapproved extension" rule.
 */
class ExtensionManagerTest {

    /** A host that records scope opens and closes and hands out a recording {@link ClientApi}. */
    private static final class FakeHost implements ExtensionHost {
        final List<String> opened = new ArrayList<>();
        final List<String> closed = new ArrayList<>();

        @Override
        public ClientApi open(String owner) {
            opened.add(owner);
            return new FakeApi(owner);
        }

        @Override
        public boolean close(String owner) {
            closed.add(owner);
            return true;
        }
    }

    /** A minimal ClientApi that counts registrations and releases them on close. */
    private static final class FakeApi implements ClientApi {
        final String owner;
        int registrations;
        int released;

        FakeApi(String owner) {
            this.owner = owner;
        }

        @Override
        public String apiVersion() {
            return "1.0";
        }

        @Override
        public Capabilities capabilities() {
            return new Capabilities(Set.of());
        }

        @Override
        public String clientVersion() {
            return "test";
        }

        private ApiHandle handle() {
            registrations++;
            return new ApiHandle() {
                boolean closed;

                @Override
                public String describe() {
                    return owner;
                }

                @Override
                public boolean active() {
                    return !closed;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        released++;
                    }
                }
            };
        }

        @Override
        public ApiHandle subscribe(Set<EventKind> kinds, Consumer<ClientEvent> listener) {
            return handle();
        }

        @Override
        public ApiHandle registerModule(Module module) {
            return handle();
        }

        @Override
        public ApiHandle registerCommand(String name, Predicate<List<String>> handler) {
            return handle();
        }

        @Override
        public ApiHandle registerHudElement(HudElement element) {
            return handle();
        }

        @Override
        public void notify(String message, String level) {
        }

        @Override
        public void log(String message) {
        }
    }

    private static final class RecordingLog implements ExtensionManager.Log {
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable error) {
            errors.add(message);
        }
    }

    private static ExtensionCandidate candidate(String id, int apiMajor, Supplier<LogvexExtension> loader) {
        ExtensionManifest manifest = ExtensionManifest.minimal(id, id, "1.0.0", apiMajor, 0);
        return new ExtensionCandidate("mod-" + id, manifest, loader);
    }

    @Test
    void discoveryNeverLoadsCode() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        boolean[] loaded = {false};
        manager.discover(candidate("quiet", 1, () -> {
            loaded[0] = true;
            return api -> { };
        }));
        assertFalse(loaded[0], "discovery must not create the entrypoint");
        assertEquals(ExtensionState.Status.AVAILABLE, manager.state("quiet").orElseThrow().status());
        assertTrue(host.opened.isEmpty(), "discovery must not open a scope");
    }

    @Test
    void enableActivatesAndRegistersUnderTheOwner() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("demo", 1, () -> api -> {
            api.registerCommand("demo", args -> true);
            api.subscribe(Set.of(EventKind.TARGET_CHANGED), event -> { });
        }));
        assertTrue(manager.enable("demo"));
        assertTrue(manager.isEnabled("demo"));
        assertEquals(List.of("demo"), host.opened);
        assertEquals(1, manager.activeCount());
    }

    @Test
    void enablingTwiceIsIdempotent() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("demo", 1, () -> api -> { }));
        assertTrue(manager.enable("demo"));
        assertTrue(manager.enable("demo"));
        assertEquals(List.of("demo"), host.opened, "a second enable must not reopen the scope");
    }

    @Test
    void aFailedInitializeRollsBackAndIsNeverActive() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("boom", 1, () -> api -> {
            api.registerCommand("boom", args -> true);
            throw new IllegalStateException("kaboom");
        }));
        assertFalse(manager.enable("boom"));
        assertFalse(manager.isEnabled("boom"));
        assertEquals(ExtensionState.Status.FAILED, manager.state("boom").orElseThrow().status());
        assertEquals(List.of("boom"), host.closed, "registrations are rolled back on failure");
        assertEquals(1, manager.failureCount());
    }

    @Test
    void aMissingEntrypointIsAFailureNotAnActiveExtension() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("ghost", 1, () -> {
            throw new IllegalStateException("no entrypoint");
        }));
        assertFalse(manager.enable("ghost"));
        assertEquals(ExtensionState.Status.FAILED, manager.state("ghost").orElseThrow().status());
    }

    @Test
    void disableReleasesAndCanBeReEnabled() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("demo", 1, () -> api -> { }));
        manager.enable("demo");
        assertTrue(manager.disable("demo"));
        assertEquals(List.of("demo"), host.closed);
        assertEquals(ExtensionState.Status.DISABLED, manager.state("demo").orElseThrow().status());
        assertTrue(manager.enable("demo"), "a disabled extension can be enabled again");
    }

    @Test
    void incompatibleManifestIsNotEnableable() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("future", 2, () -> api -> { }));
        assertEquals(ExtensionState.Status.INCOMPATIBLE, manager.state("future").orElseThrow().status());
        assertFalse(manager.enable("future"));
    }

    @Test
    void invalidManifestIsRejectedBeforeAnyCode() {
        FakeHost host = new FakeHost();
        RecordingLog log = new RecordingLog();
        ExtensionManager manager = new ExtensionManager(host, log);
        ExtensionManifest invalid = ExtensionManifest.minimal("bad id", "Bad", "1", 1, 0);
        manager.discover(new ExtensionCandidate("mod-bad", invalid, () -> api -> { }));
        assertEquals(ExtensionState.Status.INVALID, manager.state("bad id").orElseThrow().status());
        assertFalse(log.warnings.isEmpty());
    }

    @Test
    void duplicateIdsAreIgnored() {
        FakeHost host = new FakeHost();
        RecordingLog log = new RecordingLog();
        ExtensionManager manager = new ExtensionManager(host, log);
        manager.discover(candidate("dup", 1, () -> api -> { }));
        manager.discover(candidate("dup", 1, () -> api -> { }));
        assertEquals(1, manager.installed().size());
        assertTrue(log.warnings.stream().anyMatch(w -> w.contains("Duplicate")));
    }

    @Test
    void approvedExtensionsAreReEnabledAtStartup(@TempDir Path dir) {
        ExtensionPreferences preferences = new ExtensionPreferences(dir.resolve("extensions.json"));
        FakeHost host = new FakeHost();
        ExtensionManager first = new ExtensionManager(host, new RecordingLog(), preferences);
        first.discover(candidate("keeper", 1, () -> api -> { }));
        first.discover(candidate("never", 1, () -> api -> { }));
        assertTrue(first.enable("keeper"));

        // A fresh session with the persisted approvals: only the approved one comes back.
        FakeHost secondHost = new FakeHost();
        ExtensionManager second = new ExtensionManager(secondHost, new RecordingLog(), preferences);
        second.discover(candidate("keeper", 1, () -> api -> { }));
        second.discover(candidate("never", 1, () -> api -> { }));
        assertEquals(1, second.enableApproved());
        assertTrue(second.isEnabled("keeper"));
        assertFalse(second.isEnabled("never"), "an unapproved extension is never auto-enabled");
    }

    @Test
    void shutdownAllDisablesEveryActiveExtension() {
        FakeHost host = new FakeHost();
        ExtensionManager manager = new ExtensionManager(host, new RecordingLog());
        manager.discover(candidate("a", 1, () -> api -> { }));
        manager.discover(candidate("b", 1, () -> api -> { }));
        manager.enable("a");
        manager.enable("b");
        manager.shutdownAll();
        assertEquals(0, manager.activeCount());
        assertEquals(Set.of("a", "b"), Set.copyOf(host.closed));
    }
}
