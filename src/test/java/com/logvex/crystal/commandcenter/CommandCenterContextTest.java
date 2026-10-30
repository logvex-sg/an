package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the privacy-safe AI context block built from the status model and performance report. */
class CommandCenterContextTest {

    @Test
    void emptyInputsProduceAWellFormedBlock() {
        String text = CommandCenterContext.build(null, null);
        assertTrue(text.startsWith("Client status: unavailable"));
        assertTrue(text.length() <= CommandCenterContext.MAX_CHARS);
    }

    @Test
    void includesOnlySectionsThatNeedAttention() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("ok", "OK Section", StatusEntry.ok("a", "b")),
                StatusSection.of("bad", "Bad Section", StatusEntry.attention("p", "q", "detail"))), 0L);
        String text = CommandCenterContext.build(model, null);
        assertTrue(text.contains("Bad Section"));
        assertTrue(!text.contains("OK Section"));
    }

    @Test
    void listsProblemsAndPerformanceFindings() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("bad", "Bad", StatusEntry.critical("fps", "5", "dying"))), 0L);
        PerformanceReport performance = new PerformanceIntelligence().analyse(
                new PerformanceSample(10, 100, 100, 5, "Heavy", 6, 6, 1, 0, 0, 0, 0, 0L));
        String text = CommandCenterContext.build(model, performance);
        assertTrue(text.contains("fps"));
        assertTrue(text.contains("Performance"));
        assertTrue(text.contains("Heavy"));
    }

    @Test
    void outputIsCappedToKeepTheModelFast() {
        List<StatusSection> sections = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            sections.add(StatusSection.of("s" + i, "Section " + i,
                    StatusEntry.critical("k" + i, "v".repeat(40), "detail ".repeat(4))));
        }
        String text = CommandCenterContext.build(new ClientStatusModel(sections, 0L), null);
        assertTrue(text.length() <= CommandCenterContext.MAX_CHARS);
    }

    @Test
    void doesNotLeakSensitiveKeys() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("a", "Alpha", StatusEntry.ok("x", "y"))), 0L);
        String text = CommandCenterContext.build(model, null).toLowerCase(java.util.Locale.ROOT);
        assertEquals(false, text.contains("token"));
        assertEquals(false, text.contains("seed"));
    }
}
