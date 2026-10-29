package com.logvex.crystal.module.misc;

import com.logvex.crystal.integration.baritone.BaritoneService;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;

/**
 * The Baritone control surface: a diagnostics panel in the ClickGUI and the owner of the
 * navigation HUD readout.
 *
 * <p>Baritone is optional. This module enables normally when Baritone is absent; it simply
 * reports "unavailable" and its HUD element shows the reason. When Baritone is present it polls
 * the navigation state (through the shared {@link BaritoneService}, never directly), shows the
 * live goal and path status, and raises a single arrival notification when a goal the client
 * issued completes.
 *
 * <p>The module never issues goals on its own: it only displays state. Goals come from the
 * {@code ; baritone} command or the Baritone screen, both of which route through the service.
 */
public final class BaritoneModule extends Module {

    private final BooleanSetting hudElement = add("Hud",
            "Show the Baritone navigation readout on the HUD.", true);
    private final BooleanSetting notifyArrival = add("Arrival",
            "Notify when a goal you started is reached.", true);

    public BaritoneModule() {
        super(Category.MISC, "Baritone", "Baritone navigation diagnostics, controls and HUD readout.");
    }

    /** The shared Baritone service, or null before the client has bootstrapped. */
    private BaritoneService service() {
        var client = com.logvex.crystal.client.CrystalClient.get();
        return client == null ? null : client.baritone();
    }

    @Override
    protected void onEnable() {
        context().hudManager().setElementVisible("baritone", hudElement.value());
    }

    @Override
    protected void onDisable() {
        context().hudManager().setElementVisible("baritone", false);
    }

    @Override
    protected void onTick() {
        BaritoneService service = service();
        if (service == null) {
            return;
        }
        context().hudManager().setElementVisible("baritone", hudElement.value());
        if (notifyArrival.value() && service.consumeGoalReached()) {
            context().notifications().cute("Baritone: arrived \u2661");
        }
    }

    /** Exposed for tests/diagnostics: the latest status, or an unavailable status. */
    public BaritoneStatus status() {
        BaritoneService service = service();
        return service == null ? BaritoneStatus.unavailable("client not ready") : service.status();
    }

    /** True when the HUD readout is currently enabled by the module's setting. */
    public boolean hudEnabled() {
        return hudElement.value();
    }
}
