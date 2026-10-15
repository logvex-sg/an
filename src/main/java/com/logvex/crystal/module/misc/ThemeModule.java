package com.logvex.crystal.module.misc;

import com.logvex.crystal.gui.ClickGui;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;

/**
 * Theme: exposes the appearance controls as a module, so the whole theme is reachable from
 * the same module list as everything else.
 *
 * <p>It is always enabled (the theme is never "off") and acts as a small control panel:
 * choosing a preset here is identical to choosing one in the Appearance tab or with a
 * command. The preset setting drives the theme; the module never holds its own copy.
 */
public final class ThemeModule extends Module {

    private final EnumSetting<ThemePreset> preset = add("Preset", "Built-in pastel identity.", ThemePreset.FEMBOY);
    private final BooleanSetting decorations = add("Decorations", "Draw cat ears, stars and paw prints.", true);
    private final BooleanSetting catCosmetics = add("CatCosmetics", "Cute notifications and sounds.", true);
    private final BooleanSetting reducedMotion = add("ReducedMotion", "Shorten or disable animations.", false);
    private final IntSetting cornerRadius = add("CornerRadius", "Panel rounding in pixels.", 6, 0, 12);

    public ThemeModule() {
        super(Category.MISC, "Theme", "Live appearance controls for the Crystal interface.");
        alwaysEnabled();
        preset.onChange(value -> {
            if (context() != null) {
                context().theme().applyPreset(value);
            }
        });
        decorations.onChange(value -> {
            if (context() != null) {
                context().theme().settings().decorations = value;
                context().theme().update();
            }
        });
        catCosmetics.onChange(value -> {
            if (context() != null) {
                context().theme().settings().catCosmetics = value;
                context().theme().update();
            }
        });
        reducedMotion.onChange(value -> {
            if (context() != null) {
                context().theme().settings().reducedMotion = value;
                context().theme().update();
            }
        });
        cornerRadius.onChange(value -> {
            if (context() != null) {
                context().theme().settings().cornerRadius = value;
                context().theme().update();
            }
        });
    }

    @Override
    protected void onEnable() {
        // Keep the settings in step with the theme when it is loaded from disk.
        syncFromTheme();
    }

    /** Copies the current theme state into the settings (used after a config load). */
    public void syncFromTheme() {
        if (context() == null) {
            return;
        }
        var s = context().theme().settings();
        preset.setSilently(s.preset);
        decorations.setSilently(s.decorations);
        catCosmetics.setSilently(s.catCosmetics);
        reducedMotion.setSilently(s.reducedMotion);
        cornerRadius.setSilently(s.cornerRadius);
    }

    /** Opens the ClickGUI on the Appearance tab. */
    public void openAppearance() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        var bootstrap = com.logvex.crystal.client.CrystalClient.get();
        if (bootstrap != null) {
            ClickGui gui = bootstrap.clickGui();
            if (gui != null) {
                gui.showAppearance();
                gui.open();
            }
        }
    }
}
