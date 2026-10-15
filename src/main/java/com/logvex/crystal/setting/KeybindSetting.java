package com.logvex.crystal.setting;

import org.lwjgl.glfw.GLFW;

/**
 * Key binding for a module. Stores a GLFW key code; {@link #UNBOUND} ({@code -1}) means
 * no key is assigned.
 */
public final class KeybindSetting extends Setting<Integer> {

    public static final int UNBOUND = -1;

    public KeybindSetting(String name, String description, int defaultValue, boolean unboundAllowed) {
        super(name, description, defaultValue, v -> v != null && (unboundAllowed ? v >= -1 : v > 0));
    }

    public int key() {
        return get();
    }

    public boolean isBound() {
        return get() != UNBOUND;
    }

    public void set(int key) {
        super.set(key);
    }

    public void unbind() {
        super.set(UNBOUND);
    }

    @Override
    public String displayValue() {
        return isBound() ? com.logvex.crystal.util.KeyNames.name(get()) : "none";
    }

    @Override
    public String toJson() {
        return Integer.toString(get());
    }

    @Override
    public void fromJson(String json) {
        try {
            setSilently(Integer.parseInt(json.trim()));
        } catch (NumberFormatException ignored) {
            // Malformed value: keep the default.
        }
    }

    /** True when the setting holds a mouse button rather than a keyboard key. */
    public boolean isMouse() {
        return get() >= GLFW.GLFW_MOUSE_BUTTON_1 && get() <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }
}
