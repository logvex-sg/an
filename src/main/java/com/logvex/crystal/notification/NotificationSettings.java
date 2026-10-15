package com.logvex.crystal.notification;

import java.util.EnumSet;
import java.util.Set;

/**
 * The user's notification preferences: which levels are shown, how long a toast lives, the
 * scale and position, whether a sound plays, and how long identical notices are suppressed.
 *
 * <p>Plain fields with a {@link #copy()} so it can be snapshotted into a profile, and it is
 * deliberately Minecraft-independent so the filtering rules can be unit tested. The manager
 * reads it on every post; a live change therefore takes effect on the next notification.
 */
public final class NotificationSettings {

    /** Where the toast stack sits on screen. */
    public enum Position {
        TOP_RIGHT("Top right"),
        TOP_LEFT("Top left"),
        BOTTOM_RIGHT("Bottom right"),
        BOTTOM_LEFT("Bottom left");

        private final String label;

        Position(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Set<NotificationLevel> hiddenLevels = EnumSet.noneOf(NotificationLevel.class);

    /** How long a toast is displayed, in milliseconds. */
    public long durationMillis = 5200L;
    /** Text/panel scale multiplier (0.5 - 2.0). */
    public float scale = 1.0f;
    /** Where the stack is anchored. */
    public Position position = Position.TOP_RIGHT;
    /** Whether a soft sound plays when a notice is posted. */
    public boolean sound = true;
    /** Whether duplicate suppression is active. */
    public boolean suppressDuplicates = true;
    /** How long an identical message is suppressed, in milliseconds. */
    public long duplicateWindowMillis = 3000L;
    /** Maximum number of toasts kept in the visible queue. */
    public int maxVisible = 6;

    /** True when the level is not hidden. */
    public boolean shows(NotificationLevel level) {
        return level != null && !hiddenLevels.contains(level);
    }

    /** Hides or shows a level. */
    public void setShown(NotificationLevel level, boolean shown) {
        if (level == null) {
            return;
        }
        if (shown) {
            hiddenLevels.remove(level);
        } else {
            hiddenLevels.add(level);
        }
    }

    public Set<NotificationLevel> hiddenLevels() {
        return EnumSet.copyOf(hiddenLevels);
    }

    /** Clamps every numeric field to a sane range so a hand-edited config cannot break the HUD. */
    public void clamp() {
        durationMillis = Math.max(1000L, Math.min(20000L, durationMillis));
        scale = Math.max(0.5f, Math.min(2.0f, scale));
        duplicateWindowMillis = Math.max(0L, Math.min(30000L, duplicateWindowMillis));
        maxVisible = Math.max(1, Math.min(16, maxVisible));
        if (position == null) {
            position = Position.TOP_RIGHT;
        }
    }

    /** A deep copy, for snapshotting into a profile. */
    public NotificationSettings copy() {
        NotificationSettings copy = new NotificationSettings();
        copy.durationMillis = durationMillis;
        copy.scale = scale;
        copy.position = position;
        copy.sound = sound;
        copy.suppressDuplicates = suppressDuplicates;
        copy.duplicateWindowMillis = duplicateWindowMillis;
        copy.maxVisible = maxVisible;
        copy.hiddenLevels.clear();
        copy.hiddenLevels.addAll(hiddenLevels);
        return copy;
    }
}
