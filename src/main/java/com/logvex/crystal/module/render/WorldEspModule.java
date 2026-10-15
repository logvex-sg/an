package com.logvex.crystal.module.render;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.render.esp.WorldEspProvider;

/**
 * Base for modules that draw world-space ESP. Enabling registers the module as a provider
 * with the HUD (which owns the single world-render pass) and disabling removes it, so a
 * disabled module contributes nothing and costs nothing.
 *
 * <p>Registration is idempotent, so toggling the module repeatedly cannot stack duplicate
 * providers.
 */
public abstract class WorldEspModule extends Module implements WorldEspProvider {

    protected WorldEspModule(Category category, String name, String description) {
        super(category, name, description);
    }

    @Override
    protected void onEnable() {
        context().hudManager().registerWorldEsp(this);
    }

    @Override
    protected void onDisable() {
        context().hudManager().removeWorldEsp(this);
    }
}
