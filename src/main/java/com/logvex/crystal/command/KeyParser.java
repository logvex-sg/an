package com.logvex.crystal.command;

import java.util.Locale;
import org.lwjgl.glfw.GLFW;

/**
 * Parses a key name or mouse button into a GLFW code, so {@code ; bind} accepts readable
 * input ("r", "f1", "mouse2") instead of raw numbers.
 */
public final class KeyParser {

    private KeyParser() {
    }

    /** Returns the GLFW code, or null when the name is not recognised. */
    public static Integer parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.equals("none") || key.equals("unbound")) {
            return -1;
        }
        if (key.startsWith("mouse")) {
            String digits = key.substring(5).trim();
            if (digits.isEmpty()) {
                return GLFW.GLFW_MOUSE_BUTTON_1;
            }
            try {
                int index = Integer.parseInt(digits);
                if (index >= 1 && index <= GLFW.GLFW_MOUSE_BUTTON_LAST - GLFW.GLFW_MOUSE_BUTTON_1 + 1) {
                    return GLFW.GLFW_MOUSE_BUTTON_1 + index - 1;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return null;
        }
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'a' && c <= 'z') {
                return GLFW.GLFW_KEY_A + (c - 'a');
            }
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }
        if (key.startsWith("f")) {
            try {
                int number = Integer.parseInt(key.substring(1));
                if (number >= 1 && number <= 25) {
                    return GLFW.GLFW_KEY_F1 + number - 1;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the named-key table.
            }
        }
        return switch (key) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "enter", "return" -> GLFW.GLFW_KEY_ENTER;
            case "escape", "esc" -> GLFW.GLFW_KEY_ESCAPE;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "delete", "del" -> GLFW.GLFW_KEY_DELETE;
            case "up" -> GLFW.GLFW_KEY_UP;
            case "down" -> GLFW.GLFW_KEY_DOWN;
            case "left" -> GLFW.GLFW_KEY_LEFT;
            case "right" -> GLFW.GLFW_KEY_RIGHT;
            case "lshift", "shift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "rshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "lctrl", "ctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "rctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "lalt", "alt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "ralt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "grave" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            case "minus" -> GLFW.GLFW_KEY_MINUS;
            case "equal" -> GLFW.GLFW_KEY_EQUAL;
            default -> null;
        };
    }
}
