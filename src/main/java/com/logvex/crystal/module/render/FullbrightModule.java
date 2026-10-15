package com.logvex.crystal.module.render;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Fullbright: raises the client's gamma so the world is visible without torches.
 * Purely a local brightness preference — it changes no blocks and sends nothing.
 */
public final class FullbrightModule extends Module {

    private final IntSetting brightness = add("Brightness", "Target gamma as a percentage of maximum.", 100, 100, 160);

    private Double previousGamma;

    public FullbrightModule() {
        super(Category.RENDER, "Fullbright", "Raises local brightness.");
    }

    @Override
    protected void onEnable() {
        SimpleOption<Double> gamma = gamma();
        if (gamma != null) {
            previousGamma = gamma.getValue();
            gamma.setValue(brightness.value() / 100.0);
        }
    }

    @Override
    protected void onDisable() {
        SimpleOption<Double> gamma = gamma();
        if (gamma != null) {
            gamma.setValue(previousGamma != null ? previousGamma : 1.0);
        }
        previousGamma = null;
    }

    @Override
    protected void onTick() {
        SimpleOption<Double> gamma = gamma();
        if (gamma != null && Math.abs(gamma.getValue() - brightness.value() / 100.0) > 0.001) {
            gamma.setValue(brightness.value() / 100.0);
        }
    }

    private SimpleOption<Double> gamma() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.options == null ? null : client.options.getGamma();
    }
}
