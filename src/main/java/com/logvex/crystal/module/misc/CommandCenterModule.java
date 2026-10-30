package com.logvex.crystal.module.misc;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.commandcenter.CommandCenterService;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.gui.hud.CommandCenterHudElement;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;

/**
 * CommandCenter: reveals the Command Center HUD readout and gives the whole control centre a
 * home in the module list.
 *
 * <p>The Command Center itself is always available through {@code ; center} and the GUI; this
 * module only controls the compact HUD panel, which is opt-in so it does not occupy HUD space
 * for players who do not want it. Toggling it is an immediate HUD visibility change, and the
 * state is persisted like any other module.
 */
public final class CommandCenterModule extends Module {

    private final BooleanSetting hud = add("Hud", "Show the compact Command Center panel.", true);

    public CommandCenterModule() {
        super(Category.MISC, "CommandCenter", "Unified client status, compatibility and performance hub.");
        hud.onChange(value -> {
            if (context() != null) {
                context().hudManager().setElementVisible(CommandCenterHudElement.ID, value);
            }
        });
    }

    @Override
    protected void onEnable() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        CommandCenterService service = CrystalClient.get() == null ? null : CrystalClient.get().commandCenter();
        if (service != null) {
            service.snapshot();
        }
        if (context() != null) {
            context().hudManager().setElementVisible(CommandCenterHudElement.ID, hud.value());
        }
    }

    @Override
    protected void onDisable() {
        if (context() != null) {
            context().hudManager().setElementVisible(CommandCenterHudElement.ID, false);
        }
    }
}
