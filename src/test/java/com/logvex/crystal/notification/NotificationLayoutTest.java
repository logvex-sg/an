package com.logvex.crystal.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the notification toast corner placement. */
class NotificationLayoutTest {

    @Test
    void alignmentFlagsFollowTheCorner() {
        assertTrue(NotificationLayout.leftAligned(NotificationSettings.Position.TOP_LEFT));
        assertTrue(NotificationLayout.leftAligned(NotificationSettings.Position.BOTTOM_LEFT));
        assertFalse(NotificationLayout.leftAligned(NotificationSettings.Position.TOP_RIGHT));
        assertTrue(NotificationLayout.bottomAligned(NotificationSettings.Position.BOTTOM_RIGHT));
        assertFalse(NotificationLayout.bottomAligned(NotificationSettings.Position.TOP_RIGHT));
    }

    @Test
    void rightCornersHugTheRightEdge() {
        assertEquals(1000 - 120 - 4,
                NotificationLayout.rowX(NotificationSettings.Position.TOP_RIGHT, 1000, 120, 4));
        assertEquals(1000 - 120 - 4,
                NotificationLayout.rowX(NotificationSettings.Position.BOTTOM_RIGHT, 1000, 120, 4));
    }

    @Test
    void leftCornersHugTheLeftEdge() {
        assertEquals(4, NotificationLayout.rowX(NotificationSettings.Position.TOP_LEFT, 1000, 120, 4));
        assertEquals(4, NotificationLayout.rowX(NotificationSettings.Position.BOTTOM_LEFT, 1000, 120, 4));
    }

    @Test
    void topStackGrowsDownFromTheTop() {
        assertEquals(4, NotificationLayout.rowY(NotificationSettings.Position.TOP_RIGHT, 600, 0, 16, 2, 4));
        assertEquals(22, NotificationLayout.rowY(NotificationSettings.Position.TOP_RIGHT, 600, 1, 16, 2, 4));
    }

    @Test
    void bottomStackGrowsUpFromTheBottom() {
        assertEquals(600 - 4 - 16,
                NotificationLayout.rowY(NotificationSettings.Position.BOTTOM_LEFT, 600, 0, 16, 2, 4));
        assertEquals(600 - 4 - 16 - 18,
                NotificationLayout.rowY(NotificationSettings.Position.BOTTOM_LEFT, 600, 1, 16, 2, 4));
    }
}
