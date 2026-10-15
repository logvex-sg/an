package com.logvex.crystal.module.misc;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudEditorScreen;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * HudEditor: a one-click way to open the drag-and-drop HUD layout editor. Always enabled;
 * enabling it opens the editor screen rather than toggling a feature.
 */
public final class HudEditorModule extends Module {

    public HudEditorModule() {
        super(Category.MISC, "HudEditor", "Open the drag-and-drop HUD layout editor.");
    }

    @Override
    protected void onEnable() {
        if (!ScreenGuard.fontsReady()) {
            disable();
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            MinecraftClient.getInstance().setScreen(new HudEditorScreen(bootstrap.hud(), bootstrap.theme()));
        }
        // This module is an action, not a persistent toggle: enable -> open -> disable.
        disable();
    }
}
