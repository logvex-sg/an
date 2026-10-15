package com.logvex.crystal.module.hud;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;

/**
 * Base for modules that simply show/hide one HUD element. Enabling sets the element
 * visible, disabling hides it, so the module list and the HUD stay in step.
 */
public abstract class HudToggleModule extends Module {

    private final String elementId;

    protected HudToggleModule(String name, String description, String elementId) {
        super(Category.HUD, name, description);
        this.elementId = elementId;
    }

    /** The HUD element this module shows/hides. */
    public final String elementId() {
        return elementId;
    }

    @Override
    protected void onEnable() {
        context().hudManager().setElementVisible(elementId, true);
    }

    @Override
    protected void onDisable() {
        context().hudManager().setElementVisible(elementId, false);
    }
}
