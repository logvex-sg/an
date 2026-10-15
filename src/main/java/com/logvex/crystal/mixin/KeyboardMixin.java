package com.logvex.crystal.mixin;

import com.logvex.crystal.client.CrystalClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes physical key presses to the client's module keybinds.
 *
 * <p>Module keybinds are separate from vanilla {@link net.minecraft.client.option.KeyBinding}s
 * (which only exist for a fixed set of actions), so the raw GLFW callback is the natural
 * hook. This runs on the render thread via {@code MinecraftClient.execute}, and does nothing
 * while a screen is open so the ClickGUI, keybind editor and chat keep the keyboard to
 * themselves. Auto-repeat is ignored so a held key toggles a module once.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void crystal$moduleKeybinds(long window, int action, KeyInput input, CallbackInfo info) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        // Only a real press toggles; GLFW_REPEAT would spam a held key.
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        // While any screen owns the keyboard, module keybinds must not fire.
        if (MinecraftClient.getInstance().currentScreen != null) {
            return;
        }
        bootstrap.modules().onKeyPressed(input.key());
    }
}
