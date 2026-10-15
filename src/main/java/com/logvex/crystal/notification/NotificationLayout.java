package com.logvex.crystal.notification;

/**
 * Pure geometry for placing the notification toast stack.
 *
 * <p>The stack can sit in any of the four screen corners. Rows always grow away from the
 * anchored edge (down from the top, up from the bottom), so the newest toast is nearest the
 * corner and older ones march toward the middle. Kept Minecraft-independent so the corner
 * maths is unit tested without a client.
 */
public final class NotificationLayout {

    private NotificationLayout() {
    }

    /** True when the position anchors to the left edge. */
    public static boolean leftAligned(NotificationSettings.Position position) {
        return position == NotificationSettings.Position.TOP_LEFT
                || position == NotificationSettings.Position.BOTTOM_LEFT;
    }

    /** True when the position anchors to the bottom edge. */
    public static boolean bottomAligned(NotificationSettings.Position position) {
        return position == NotificationSettings.Position.BOTTOM_RIGHT
                || position == NotificationSettings.Position.BOTTOM_LEFT;
    }

    /** The x of a row of the given width, anchored in the position's horizontal corner. */
    public static int rowX(NotificationSettings.Position position, int screenWidth, int width, int margin) {
        return leftAligned(position) ? margin : screenWidth - width - margin;
    }

    /** The y of the {@code index}-th row, stacking away from the anchored vertical edge. */
    public static int rowY(NotificationSettings.Position position, int screenHeight, int index,
                           int rowHeight, int gap, int margin) {
        int step = rowHeight + gap;
        if (bottomAligned(position)) {
            return screenHeight - margin - rowHeight - index * step;
        }
        return margin + index * step;
    }
}
