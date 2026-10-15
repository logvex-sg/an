package com.logvex.crystal.util;

import org.lwjgl.glfw.GLFW;

/** Maps GLFW key/mouse codes to readable names for the GUI and commands. */
public final class KeyNames {

    private KeyNames() {
    }

    public static String name(int key) {
        if (key == -1) {
            return "none";
        }
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
            return switch (key) {
                case GLFW.GLFW_MOUSE_BUTTON_1 -> "Mouse L";
                case GLFW.GLFW_MOUSE_BUTTON_2 -> "Mouse R";
                case GLFW.GLFW_MOUSE_BUTTON_3 -> "Mouse M";
                default -> "Mouse " + (key - GLFW.GLFW_MOUSE_BUTTON_1 + 1);
            };
        }
        String glfw = GLFW.glfwGetKeyName(key, 0);
        if (glfw != null && !glfw.isBlank()) {
            return glfw.toUpperCase();
        }
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "Key " + key;
        };
    }
}
