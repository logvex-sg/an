package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the standard action set through a recording host, and the per-module toggle actions. */
class StandardActionsTest {

    /** A host that records what it was asked to do instead of touching the live client. */
    private static final class RecordingHost implements CommandCenterHost {
        final List<String> notifications = new ArrayList<>();
        final List<String> guiViews = new ArrayList<>();
        boolean hudEditorOpened;
        boolean saved;
        boolean reloaded;
        boolean printedDiagnostics;
        boolean printedConflicts;
        boolean printedErrors;
        boolean clearedErrors;
        boolean toggleResult = true;

        @Override
        public void notify(String message, boolean error) {
            notifications.add((error ? "!" : "?") + message);
        }

        @Override
        public void openGui(String view) {
            guiViews.add(view == null ? "" : view);
        }

        @Override
        public void openHudEditor() {
            hudEditorOpened = true;
        }

        @Override
        public void saveAll() {
            saved = true;
        }

        @Override
        public void reloadAll() {
            reloaded = true;
        }

        @Override
        public boolean toggleModule(String name) {
            return toggleResult;
        }

        @Override
        public boolean setModule(String name, boolean enabled) {
            return true;
        }

        @Override
        public void printDiagnostics() {
            printedDiagnostics = true;
        }

        @Override
        public void printConflicts() {
            printedConflicts = true;
        }

        @Override
        public void printErrors(boolean clear) {
            printedErrors = true;
            clearedErrors = clear;
        }

        @Override
        public ClientStatusModel status() {
            return new ClientStatusModel(List.of(
                    StatusSection.of("a", "Alpha", StatusEntry.ok("x", "y"))), 0L);
        }

        @Override
        public PerformanceReport performance() {
            return new PerformanceIntelligence().analyse(PerformanceSample.empty(0L));
        }
    }

    private final RecordingHost host = new RecordingHost();
    private final CommandRegistry registry = new CommandRegistry();

    private StandardActions actions() {
        StandardActions actions = new StandardActions(host, registry);
        actions.registerAll();
        return actions;
    }

    @Test
    void registersADiscoverableStandardSet() {
        actions();
        assertTrue(registry.contains("status.report"));
        assertTrue(registry.contains("diagnostics.conflicts"));
        assertTrue(registry.contains("performance.report"));
        assertTrue(registry.contains("interface.clickgui"));
        assertTrue(registry.contains("interface.hud-editor"));
        assertTrue(registry.contains("config.save"));
        assertTrue(registry.size() >= 12);
    }

    @Test
    void openClickGuiActionCallsTheHost() {
        actions();
        assertTrue(registry.run("interface.clickgui").success());
        assertEquals(List.of(""), host.guiViews);
    }

    @Test
    void openAppearancePassesTheViewName() {
        actions();
        assertTrue(registry.run("interface.appearance").success());
        assertEquals(List.of("appearance"), host.guiViews);
    }

    @Test
    void hudEditorAndSaveReachTheHost() {
        actions();
        registry.run("interface.hud-editor");
        registry.run("config.save");
        registry.run("config.reload");
        assertTrue(host.hudEditorOpened);
        assertTrue(host.saved);
        assertTrue(host.reloaded);
    }

    @Test
    void diagnosticsActionsForwardToTheHost() {
        actions();
        registry.run("diagnostics.conflicts");
        registry.run("diagnostics.errors");
        registry.run("diagnostics.clear-errors");
        registry.run("diagnostics.dump");
        assertTrue(host.printedConflicts);
        assertTrue(host.printedErrors);
        assertTrue(host.clearedErrors);
        assertTrue(host.printedDiagnostics);
    }

    @Test
    void statusReportNotifiesWithTheSummary() {
        actions();
        assertTrue(registry.run("status.report").success());
        assertFalse(host.notifications.isEmpty());
    }

    @Test
    void moduleTogglesAreRegisteredPerNameAndReportFailures() {
        actions().registerModuleToggles(List.of("AutoCrystal", "Surround"));
        assertTrue(registry.contains("module.toggle.autocrystal"));
        assertTrue(registry.run("module.toggle.autocrystal").success());

        host.toggleResult = false;
        ActionResult result = registry.run("module.toggle.surround");
        assertFalse(result.success());
        assertTrue(result.message().contains("Surround"));
    }

    @Test
    void eachActionHasIdLabelCategoryAndDescription() {
        actions();
        for (CommandCenterAction action : registry.all()) {
            assertNotNull(action.id());
            assertNotNull(action.label());
            assertNotNull(action.category());
            assertNotNull(action.description());
        }
    }
}
