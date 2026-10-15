package com.logvex.crystal.util;

/**
 * Shared formatting helpers so every HUD, label and notification renders distances,
 * coordinates and counts the same way. Keeping the format in one place means a change to
 * (say) the distance suffix does not have to be hunted through a dozen modules.
 */
public final class Format {

    private Format() {
    }

    /** Formats a distance in blocks, e.g. {@code 12.3m}. */
    public static String distance(double blocks) {
        return String.format("%.1fm", blocks);
    }

    /** Formats a coordinate for a label, e.g. {@code 128, 64, -203}. */
    public static String coords(double x, double y, double z) {
        return String.format("%.0f, %.0f, %.0f", x, y, z);
    }

    /** Formats a whole-block coordinate pair for a 2-D map label. */
    public static String coords2d(int x, int z) {
        return x + ", " + z;
    }

    /** Formats a 0..1 fraction as a whole percentage. */
    public static String percent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100) + "%";
    }

    /** Formats a duration in seconds as {@code h:mm:ss} / {@code m:ss}. */
    public static String duration(long seconds) {
        long s = Math.max(0, seconds);
        long hours = s / 3600;
        long minutes = (s % 3600) / 60;
        long secs = s % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /** Formats a signed coordinate delta, e.g. {@code +128} / {@code -64}. */
    public static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }

    /** Truncates a string to a maximum length, appending an ellipsis when cut. */
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text == null ? "" : text;
        }
        return max <= 1 ? text.substring(0, max) : text.substring(0, max - 1) + "\u2026";
    }
}
