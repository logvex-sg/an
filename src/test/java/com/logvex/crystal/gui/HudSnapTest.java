package com.logvex.crystal.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the HUD editor's pure snapping/overlap helpers. */
class HudSnapTest {

    @Test
    void snapRoundsToTheGrid() {
        assertEquals(0, HudSnap.snap(3, 8));
        assertEquals(8, HudSnap.snap(5, 8));
        assertEquals(16, HudSnap.snap(13, 8));
        assertEquals(7, HudSnap.snap(7, 1), "a grid of one leaves the value alone");
    }

    @Test
    void clampKeepsValuesInRange() {
        assertEquals(0, HudSnap.clamp(-5, 0, 100));
        assertEquals(100, HudSnap.clamp(500, 0, 100));
        assertEquals(42, HudSnap.clamp(42, 0, 100));
    }

    @Test
    void overlapDetection() {
        HudSnap.Rect a = new HudSnap.Rect(0, 0, 10, 10);
        assertTrue(HudSnap.overlaps(a, new HudSnap.Rect(5, 5, 10, 10)));
        assertFalse(HudSnap.overlaps(a, new HudSnap.Rect(10, 0, 10, 10)), "touching edges do not overlap");
        assertFalse(HudSnap.overlaps(a, new HudSnap.Rect(20, 20, 5, 5)));
    }

    @Test
    void findOverlapsReportsEachElementOnce() {
        Map<String, HudSnap.Rect> rects = new LinkedHashMap<>();
        rects.put("a", new HudSnap.Rect(0, 0, 20, 20));
        rects.put("b", new HudSnap.Rect(10, 10, 20, 20));
        rects.put("c", new HudSnap.Rect(200, 200, 20, 20));
        Map<String, String> overlaps = HudSnap.findOverlaps(rects);
        assertEquals(2, overlaps.size());
        assertEquals("b", overlaps.get("a"));
        assertEquals("a", overlaps.get("b"));
        assertFalse(overlaps.containsKey("c"));
    }

    @Test
    void resetRestoresAutoStacking() {
        HudLayout.Entry entry = new HudLayout.Entry();
        entry.customPosition = true;
        entry.x = 120;
        entry.y = 80;
        entry.opacity = 0.4f;
        entry.scale = 1.8f;
        entry.background = true;
        entry.border = true;
        HudSnap.reset(entry);
        assertFalse(entry.customPosition);
        assertEquals(0, entry.x);
        assertEquals(0, entry.y);
        assertEquals(1.0f, entry.opacity);
        assertEquals(1.0f, entry.scale);
        assertFalse(entry.background);
        assertFalse(entry.border);
    }

    @Test
    void rectContainsUsesHalfOpenBounds() {
        HudSnap.Rect rect = new HudSnap.Rect(0, 0, 10, 10);
        assertTrue(rect.contains(0, 0));
        assertTrue(rect.contains(9, 9));
        assertFalse(rect.contains(10, 10));
    }
}
