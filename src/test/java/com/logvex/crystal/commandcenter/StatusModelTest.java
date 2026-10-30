package com.logvex.crystal.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the status value types: entry formatting, section roll-up and model aggregation. */
class StatusModelTest {

    @Test
    void healthStatusOrdersBySeverity() {
        assertFalse(HealthStatus.OK.needsAttention());
        assertTrue(HealthStatus.INFO.needsAttention());
        assertEquals(HealthStatus.CRITICAL, HealthStatus.OK.worseOf(HealthStatus.CRITICAL));
        assertEquals(HealthStatus.ATTENTION, HealthStatus.ATTENTION.worseOf(HealthStatus.INFO));
        assertEquals(HealthStatus.ATTENTION, HealthStatus.ATTENTION.worseOf(null));
    }

    @Test
    void entryDescribeIncludesDetailOnlyWhenPresent() {
        assertEquals("FPS: 60", StatusEntry.ok("FPS", "60").describe());
        assertEquals("FPS: 12 \u2014 below playable",
                StatusEntry.critical("FPS", "12", "below playable").describe());
    }

    @Test
    void sectionRollsUpWorstStatusAndProblems() {
        StatusSection section = StatusSection.of("mods", "Modules",
                StatusEntry.ok("A", "on"),
                StatusEntry.attention("B", "lagging", "3ms"),
                StatusEntry.critical("C", "crashed", "NPE"));
        assertEquals(HealthStatus.CRITICAL, section.worst());
        assertEquals(2, section.problemCount());
        assertEquals(3, section.entries().size());
        assertFalse(section.isEmpty());
    }

    @Test
    void emptySectionIsOk() {
        StatusSection section = new StatusSection("empty", "Empty", "", List.of());
        assertTrue(section.isEmpty());
        assertEquals(HealthStatus.OK, section.worst());
    }

    @Test
    void modelAggregatesSectionsAndFindsProblems() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("a", "A", StatusEntry.ok("x", "y")),
                StatusSection.of("b", "B", StatusEntry.attention("p", "q", ""))), 123L);
        assertEquals(HealthStatus.ATTENTION, model.overall());
        assertEquals(1, model.problemCount());
        assertFalse(model.healthy());
        assertEquals(123L, model.capturedAtMillis());
        assertEquals("B", model.section("b").title());
        assertEquals(2, model.sections().size());
    }

    @Test
    void modelSummaryCountsCriticalSections() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("a", "A", StatusEntry.critical("x", "y", "")),
                StatusSection.of("b", "B", StatusEntry.ok("p", "q"))), 0L);
        assertTrue(model.summary().contains("critical"), model.summary());
    }

    @Test
    void modelWithNoProblemsIsHealthy() {
        ClientStatusModel model = new ClientStatusModel(List.of(
                StatusSection.of("a", "A", StatusEntry.ok("x", "y"))), 0L);
        assertTrue(model.healthy());
        assertEquals("all clear", model.summary());
    }
}
