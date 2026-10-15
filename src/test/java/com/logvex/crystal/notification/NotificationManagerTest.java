package com.logvex.crystal.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Behavioural tests for the notification queue: filtering, suppression and history. */
class NotificationManagerTest {

    @Test
    void postsAreQueuedInOrder() {
        NotificationManager manager = new NotificationManager();
        manager.info("one");
        manager.info("two");
        assertEquals(2, manager.size());
        assertEquals("one", manager.active().get(0).text());
        assertEquals("two", manager.active().get(1).text());
    }

    @Test
    void hiddenLevelIsDropped() {
        NotificationManager manager = new NotificationManager();
        manager.settings().setShown(NotificationLevel.WARN, false);
        manager.warn("hidden");
        manager.info("shown");
        assertEquals(1, manager.size());
        assertEquals("shown", manager.active().get(0).text());
    }

    @Test
    void duplicateIsSuppressedWithinTheWindow() {
        NotificationManager manager = new NotificationManager();
        manager.info("same");
        manager.info("same");
        assertEquals(1, manager.size());
        // A different message is never suppressed.
        manager.info("different");
        assertEquals(2, manager.size());
    }

    @Test
    void suppressionCanBeTurnedOff() {
        NotificationManager manager = new NotificationManager();
        manager.settings().suppressDuplicates = false;
        manager.info("same");
        manager.info("same");
        assertEquals(2, manager.size());
    }

    @Test
    void historySurvivesExpiryAndIsBounded() {
        NotificationManager manager = new NotificationManager();
        for (int i = 0; i < 80; i++) {
            manager.info("message " + i);
        }
        assertEquals(64, manager.history().size());
        manager.clearHistory();
        assertTrue(manager.history().isEmpty());
    }

    @Test
    void blankMessagesAreIgnored() {
        NotificationManager manager = new NotificationManager();
        manager.info("   ");
        manager.info(null);
        assertEquals(0, manager.size());
    }

    @Test
    void actionBarFlagIsRecorded() {
        NotificationManager manager = new NotificationManager();
        manager.actionBar("hi", NotificationLevel.SUCCESS);
        assertTrue(manager.active().get(0).actionBar());
    }

    @Test
    void listenerIsInvokedForAcceptedPostsOnly() {
        NotificationManager manager = new NotificationManager();
        int[] count = {0};
        manager.addListener(notification -> count[0]++);
        manager.info("accepted");
        manager.settings().setShown(NotificationLevel.WARN, false);
        manager.warn("dropped");
        assertEquals(1, count[0]);
    }

    @Test
    void alphaUsesTheGivenLifetime() {
        NotificationManager manager = new NotificationManager();
        manager.info("fresh");
        NotificationManager.Notification notification = manager.active().get(0);
        assertEquals(1.0f, NotificationManager.alpha(notification, 60000L), 0.05f);
        assertFalse(NotificationManager.alpha(notification, 0L) < 1f, "zero lifetime means no fade");
    }
}
