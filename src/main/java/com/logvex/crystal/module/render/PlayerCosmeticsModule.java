package com.logvex.crystal.module.render;

import com.logvex.crystal.cosmetic.PlayerCosmetics;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;

/**
 * PlayerCosmetics: optional, purely cosmetic accessories drawn on players (cat ears, a
 * pastel bow, a halo).
 *
 * <p>The module is the on/off switch and the settings panel; the actual geometry lives in
 * the {@code cosmetic.render} feature renderer, which reads {@link PlayerCosmetics}. It
 * changes nothing about the player entity, so it cannot affect combat, movement or any
 * server interaction. The theme's "decorations" and "cat cosmetics" toggles gate it live,
 * and "reduced motion" freezes the idle sway — all without a restart.
 */
public final class PlayerCosmeticsModule extends Module {

    private final EnumSetting<PlayerCosmetics.Style> style =
            add("Style", "Which accessory to wear.", PlayerCosmetics.Style.CAT_EARS);
    private final BooleanSetting animate =
            add("Animate", "Gentle idle sway (off during reduced motion).", true);
    private final IntSetting glow =
            add("Glow", "Strength of the soft glow and sway.", 50, 0, 100);

    public PlayerCosmeticsModule() {
        super(Category.RENDER, "PlayerCosmetics", "Optional pastel accessories on players.");
        style.onChange(value -> PlayerCosmetics.setStyle(value));
        animate.onChange(value -> PlayerCosmetics.setAnimate(value));
        glow.onChange(value -> PlayerCosmetics.setGlow(value / 100f));
    }

    @Override
    protected void onEnable() {
        PlayerCosmetics.setEnabled(true);
        push();
    }

    @Override
    protected void onDisable() {
        PlayerCosmetics.setEnabled(false);
    }

    @Override
    protected void onTick() {
        // Re-read the live theme gate every tick so toggling decorations/cat cosmetics or
        // reduced motion takes effect immediately.
        push();
    }

    private void push() {
        PlayerCosmetics.setStyle(style.value());
        PlayerCosmetics.setAnimate(animate.value());
        PlayerCosmetics.setGlow(glow.value() / 100f);
        if (context() != null) {
            var s = context().theme().settings();
            PlayerCosmetics.setCuteGate(s.decorations && s.catCosmetics && !s.reducedMotion);
        }
    }
}
