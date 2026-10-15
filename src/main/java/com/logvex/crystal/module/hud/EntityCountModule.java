package com.logvex.crystal.module.hud;

/** Shows a per-category count of the entities the client can see. */
public final class EntityCountModule extends HudToggleModule {

    public EntityCountModule() {
        super("EntityCounter", "Shows a count of nearby entities by category.", "entitycount");
    }
}
