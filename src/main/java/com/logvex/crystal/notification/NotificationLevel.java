package com.logvex.crystal.notification;

/**
 * Severity of a notification, with an original cute glyph and a pastel tint key. The tint
 * key is resolved against the active {@code Palette} at render time so notifications
 * follow the theme.
 */
public enum NotificationLevel {

    INFO("\u2727", 0xFFA0D8FF),
    SUCCESS("\u2661", 0xFFFFA6C9),
    WARN("\u26A0", 0xFFFFC46B),
    ERROR("\u2716", 0xFFFF6B85),
    CUTE("\u2764", 0xFFFF9EC4);

    private final String glyph;
    private final int fallbackColor;

    NotificationLevel(String glyph, int fallbackColor) {
        this.glyph = glyph;
        this.fallbackColor = fallbackColor;
    }

    /** Decorative symbol prefixed to the notification text. */
    public String glyph() {
        return glyph;
    }

    /** Colour used when no palette is available. */
    public int fallbackColor() {
        return fallbackColor;
    }
}
