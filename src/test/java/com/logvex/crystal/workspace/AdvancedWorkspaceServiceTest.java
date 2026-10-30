package com.logvex.crystal.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.logvex.crystal.config.AppearanceStore;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.config.HudLayoutStore;
import com.logvex.crystal.config.ProfileManager;
import com.logvex.crystal.config.ProfileService;
import com.logvex.crystal.gui.HudLayout;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.theme.ThemeSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers advanced workspaces: selective apply (theme only, HUD only) and the change preview.
 * Uses the real config layers over a temp directory - an empty module manager and the live HUD
 * and theme objects - so the whole config path is exercised without a client.
 */
class AdvancedWorkspaceServiceTest {

    @TempDir
    Path dir;

    private AdvancedWorkspaceService workspaces;
    private ThemeSettings themeSettings;

    @BeforeEach
    void setUp() {
        ProfileManager manager = new ProfileManager(dir.resolve("profiles"));
        ConfigManager config = new ConfigManager(new ModuleManager(), dir.resolve("modules.json"));
        HudLayoutStore hudStore = new HudLayoutStore(dir.resolve("hud.json"));
        AppearanceStore appearanceStore = new AppearanceStore(dir.resolve("appearance.json"));
        HudLayout hudLayout = new HudLayout();
        themeSettings = new ThemeSettings();
        ProfileService profiles = new ProfileService(manager, config, hudStore, appearanceStore,
                hudLayout, themeSettings, () -> { });
        workspaces = new AdvancedWorkspaceService(profiles);
    }

    @Test
    void parseSectionsHandlesNamesAndAll() {
        assertEquals(Set.of(AdvancedWorkspaceService.Section.MODULES,
                        AdvancedWorkspaceService.Section.HUD,
                        AdvancedWorkspaceService.Section.APPEARANCE),
                AdvancedWorkspaceService.parseSections("all"));
        assertEquals(Set.of(AdvancedWorkspaceService.Section.HUD),
                AdvancedWorkspaceService.parseSections("hud"));
        assertEquals(Set.of(AdvancedWorkspaceService.Section.HUD,
                        AdvancedWorkspaceService.Section.APPEARANCE),
                AdvancedWorkspaceService.parseSections("hud, appearance"));
        assertEquals(Set.of(), AdvancedWorkspaceService.parseSections("nonsense"));
    }

    @Test
    void savingAndListingAWorkspaceWorks() throws IOException {
        assertTrue(workspaces.save("pvp", true));
        assertTrue(workspaces.names().contains("pvp"));
        assertFalse(workspaces.save("pvp", false), "no overwrite without the flag");
    }

    @Test
    void selectiveApplyRestoresOnlyTheRequestedSection() throws IOException {
        themeSettings.backgroundOpacity = 0.9f;
        workspaces.save("base", true);

        // Change the live theme away from the saved workspace.
        themeSettings.backgroundOpacity = 0.1f;
        assertTrue(workspaces.diff("base").appearance() > 0, "the theme differs from the workspace");

        // Applying only APPEARANCE pulls the saved theme back...
        assertTrue(workspaces.applySections("base",
                Set.of(AdvancedWorkspaceService.Section.APPEARANCE)));
        assertEquals(0.9f, themeSettings.backgroundOpacity, 0.0001f);
        assertEquals(0, workspaces.diff("base").appearance());
    }

    @Test
    void emptyOrUnknownApplyIsANoOp() throws IOException {
        workspaces.save("base", true);
        assertFalse(workspaces.applySections("missing",
                Set.of(AdvancedWorkspaceService.Section.HUD)), "unknown workspace");
        assertFalse(workspaces.applySections("base", Set.of()), "empty section set");
    }

    @Test
    void diffCountsChangedKeys() {
        JsonObject a = new JsonObject();
        a.addProperty("speed", 1);
        a.addProperty("only-a", true);
        JsonObject b = new JsonObject();
        b.addProperty("speed", 2);
        b.addProperty("only-b", true);
        // speed differs, only-a removed, only-b added -> 3 changes.
        assertEquals(3, AdvancedWorkspaceService.countChanges(a, b));
        assertEquals(0, AdvancedWorkspaceService.countChanges(null, b));
        assertEquals(0, AdvancedWorkspaceService.countChanges(a, a));
    }

    @Test
    void diffDescriptionReadsWell() {
        assertEquals("no changes", new AdvancedWorkspaceService.Diff(0, 0, 0).describe());
        assertTrue(new AdvancedWorkspaceService.Diff(1, 0, 0).describe().contains("1 change"));
        assertTrue(new AdvancedWorkspaceService.Diff(1, 1, 1).describe().contains("3 changes"));
    }
}
