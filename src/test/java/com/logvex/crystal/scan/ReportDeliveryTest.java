package com.logvex.crystal.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.notification.NotificationLevel;
import com.logvex.crystal.notification.NotificationManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that a found cluster reaches the local notification queue with the expected text.
 * Uses the real {@link NotificationManager} so the delivery path is exercised, not mocked.
 */
class ReportDeliveryTest {

    @Test
    void reportReachesTheNotificationQueue() {
        NotificationManager notifications = new NotificationManager();
        List<NotificationManager.Notification> delivered = new ArrayList<>();
        notifications.addListener(delivered::add);

        ChestReport report = new ChestReport("minecraft:overworld@1,4,0", "minecraft:overworld",
                16, 64, 4, 42, 1_000L, List.of("Alex"));
        notifications.notify(ReportPresenter.notificationText(report), NotificationLevel.CUTE);

        assertEquals(1, delivered.size());
        String text = delivered.get(0).text();
        assertTrue(text.contains("42 chests"), text);
        assertTrue(text.contains("16 64 4"), text);
        assertTrue(text.contains("Alex"), text);
        assertEquals(NotificationLevel.CUTE, delivered.get(0).level());
        assertEquals(1, notifications.size());
    }

    @Test
    void notificationTextIsStableAndCarriesTheCuteGlyph() {
        ChestReport report = new ChestReport("id", "minecraft:the_nether", 0, 70, 0, 30,
                1_000L, List.of());
        String text = ReportPresenter.notificationText(report);
        assertTrue(text.startsWith("\u2661"), "the notification keeps the theme glyph");
        assertTrue(text.contains("30 chests"));
        assertTrue(text.contains("nether"));
    }

    @Test
    void logLineCarriesTheClusterId() {
        ChestReport report = new ChestReport("minecraft:overworld@2,4,-1", "minecraft:overworld",
                35, 64, -3, 40, 1_000L, List.of());
        String line = ReportPresenter.logLine(report);
        assertTrue(line.contains("minecraft:overworld@2,4,-1"), line);
    }

    @Test
    void summaryWithoutPlayersOmitsThePlayerSection() {
        ChestReport report = new ChestReport("id", "minecraft:overworld", 0, 64, 0, 30, 1_000L, List.of());
        assertTrue(!report.summary().contains("players nearby"), report.summary());
    }
}
