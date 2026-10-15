package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Verifies the debounced autosave gate: it coalesces bursts and flushes on demand. */
class AutoSaveTest {

    @Test
    void doesNotSaveBeforeTheWindowElapses() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(1000, saves::incrementAndGet);

        auto.markDirty(0);
        assertFalse(auto.tick(999));
        assertEquals(0, saves.get());
        assertTrue(auto.isDirty());
    }

    @Test
    void savesOnceTheWindowElapses() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(1000, saves::incrementAndGet);

        auto.markDirty(0);
        assertTrue(auto.tick(1000));
        assertEquals(1, saves.get());
        assertFalse(auto.isDirty());
    }

    @Test
    void aBurstOfChangesCollapsesIntoOneSave() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(1000, saves::incrementAndGet);

        // Ten changes inside the window must not extend the timer or cause ten writes.
        for (int i = 0; i < 10; i++) {
            auto.markDirty(i * 50L);
        }
        assertFalse(auto.tick(999));
        assertTrue(auto.tick(1000));
        assertEquals(1, saves.get());
    }

    @Test
    void timerStartsAtTheFirstChangeOfABurst() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(1000, saves::incrementAndGet);

        auto.markDirty(0);
        auto.markDirty(900); // must NOT reset the window
        assertTrue(auto.tick(1000), "window is measured from the first change");
        assertEquals(1, saves.get());
    }

    @Test
    void tickWithNothingDirtyIsANoOp() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(10, saves::incrementAndGet);
        assertFalse(auto.tick(100_000));
        assertEquals(0, saves.get());
    }

    @Test
    void flushSavesImmediatelyRegardlessOfWindow() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(60_000, saves::incrementAndGet);

        auto.markDirty(0);
        auto.flush(10);
        assertEquals(1, saves.get());
        assertFalse(auto.isDirty());
        assertEquals(10, auto.lastSaveMillis());
    }

    @Test
    void flushWithNothingPendingDoesNotSave() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(60_000, saves::incrementAndGet);
        auto.flush(10);
        assertEquals(0, saves.get());
    }

    @Test
    void aSecondChangeAfterASaveIsDirtyAgain() {
        AtomicInteger saves = new AtomicInteger();
        AutoSave auto = new AutoSave(1000, saves::incrementAndGet);

        auto.markDirty(0);
        auto.tick(1000);
        auto.markDirty(1500);
        assertTrue(auto.isDirty());
        auto.tick(2500);
        assertEquals(2, saves.get());
        assertEquals(2, auto.saveCount());
    }
}
