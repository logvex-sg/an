package com.logvex.crystal.gui.widget;

/**
 * Pure RGBA channel math for the inline colour picker. Kept separate from the screen so
 * the value manipulation can be unit tested without a GUI.
 */
public final class ColorPicker {

    public enum Channel {
        RED, GREEN, BLUE, ALPHA
    }

    private ColorPicker() {
    }

    public static int component(int argb, Channel channel) {
        return switch (channel) {
            case RED -> (argb >> 16) & 0xFF;
            case GREEN -> (argb >> 8) & 0xFF;
            case BLUE -> argb & 0xFF;
            case ALPHA -> (argb >>> 24) & 0xFF;
        };
    }

    public static int withComponent(int argb, Channel channel, int value) {
        int v = Math.max(0, Math.min(255, value));
        return switch (channel) {
            case RED -> (argb & 0xFF00FFFF) | (v << 16);
            case GREEN -> (argb & 0xFFFF00FF) | (v << 8);
            case BLUE -> (argb & 0xFFFFFF00) | v;
            case ALPHA -> (argb & 0x00FFFFFF) | (v << 24);
        };
    }

    /** Maps a drag along a slider of the given pixel width to a channel value. */
    public static int valueForX(double mouseX, int sliderLeft, int sliderWidth) {
        double ratio = (mouseX - sliderLeft) / Math.max(1.0, sliderWidth);
        return (int) Math.round(Math.max(0, Math.min(1, ratio)) * 255);
    }

    /**
     * The colour shown at the right end of a channel's slider: the current colour with
     * that one channel at full strength, so a black-to-this gradient reads as the value.
     */
    public static int channelGradient(int argb, Channel channel) {
        return withComponent(argb, channel, 255);
    }
}
