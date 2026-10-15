package com.logvex.crystal.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies the bounded module error log used by the {@code ; errors} diagnostic. */
class ModuleErrorLogTest {

    @Test
    void recordsAndCountsByModule() {
        ModuleErrorLog log = new ModuleErrorLog(8);
        log.record("AutoCrystal", "boom");
        log.record("AutoCrystal", "boom again");
        log.record("Surround", "different");

        assertEquals(3, log.totalRecorded());
        assertEquals(2, log.distinctModules());

        List<Map.Entry<String, Integer>> ranked = log.ranked();
        assertEquals("AutoCrystal", ranked.get(0).getKey());
        assertEquals(2, ranked.get(0).getValue());
    }

    @Test
    void retainsOnlyTheMostRecentEntries() {
        ModuleErrorLog log = new ModuleErrorLog(3);
        for (int i = 0; i < 10; i++) {
            log.record("M", "err " + i);
        }
        List<ModuleErrorLog.Entry> entries = log.entries();
        assertEquals(3, entries.size());
        assertEquals("err 9", entries.get(entries.size() - 1).message());
        // The total count still reflects every failure, even the evicted ones.
        assertEquals(10, log.totalRecorded());
    }

    @Test
    void latestReturnsTheMostRecentOrNull() {
        ModuleErrorLog log = new ModuleErrorLog(4);
        assertNull(log.latest());
        log.record("A", "first");
        log.record("B", "second");
        assertNotNull(log.latest());
        assertEquals("B", log.latest().module());
        assertEquals("second", log.latest().message());
    }

    @Test
    void describePreservesTheOriginalMessage() {
        assertEquals("IllegalStateException: nope",
                ModuleErrorLog.describe(new IllegalStateException("nope")));
        assertEquals("RuntimeException", ModuleErrorLog.describe(new RuntimeException()));
        assertEquals("", ModuleErrorLog.describe(null));
    }

    @Test
    void recordFromThrowableUsesTheDescribedMessage() {
        ModuleErrorLog log = new ModuleErrorLog(4);
        log.record("Mod", new IllegalArgumentException("bad input"));
        assertEquals("IllegalArgumentException: bad input", log.latest().message());
    }

    @Test
    void nullModuleNameFallsBackToUnknown() {
        ModuleErrorLog log = new ModuleErrorLog(4);
        log.record(null, "x");
        assertEquals("unknown", log.latest().module());
    }

    @Test
    void clearEmptiesEverything() {
        ModuleErrorLog log = new ModuleErrorLog(4);
        log.record("A", "x");
        log.clear();
        assertEquals(0, log.totalRecorded());
        assertEquals(0, log.distinctModules());
        assertTrue(log.entries().isEmpty());
        assertNull(log.latest());
    }
}
