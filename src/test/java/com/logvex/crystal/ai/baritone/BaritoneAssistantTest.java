package com.logvex.crystal.ai.baritone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.integration.baritone.BaritoneHistory;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import org.junit.jupiter.api.Test;

/**
 * The deterministic Baritone advisor. These rules must work with no model at all, so they are
 * tested directly against hand-built {@link BaritoneStatus} snapshots.
 */
class BaritoneAssistantTest {

    private BaritoneStatus status(boolean hasGoal, boolean pathing, boolean calculating,
                                  boolean hasPath, boolean paused, boolean safeToCancel) {
        return new BaritoneStatus(true, "1.10.2", "", hasGoal, hasGoal ? "GoalBlock" : "none",
                hasGoal ? "10 64 -20" : "", 10, 64, -20, pathing, calculating, hasPath,
                hasPath ? 12 : -1, pathing ? 200 : -1, paused, safeToCancel,
                hasGoal ? "Goal set" : "Idle");
    }

    private BaritoneHistory history() {
        return new BaritoneHistory(16);
    }

    @Test
    void unavailableStatusProducesGuidanceNotAnError() {
        BaritoneAdvice advice = BaritoneAssistant.advise(BaritoneStatus.unavailable("not installed"),
                history(), "", false);
        assertFalse(advice.available());
        assertTrue(advice.summary().toLowerCase().contains("not available"));
        assertFalse(advice.suggestions().isEmpty(), "the user is still told what to do");
    }

    @Test
    void nullStatusIsHandled() {
        BaritoneAdvice advice = BaritoneAssistant.advise(null, history(), null, false);
        assertFalse(advice.available());
    }

    @Test
    void goalWithoutPathIsFlaggedAsPossiblyUnreachable() {
        BaritoneAdvice advice = BaritoneAssistant.advise(
                status(true, false, false, false, false, true), history(), "", false);
        assertTrue(advice.available());
        assertTrue(advice.likelyIssues().stream().anyMatch(i -> i.contains("unreachable")));
        assertTrue(advice.suggestions().stream().anyMatch(s -> s.toLowerCase().contains("closer")));
    }

    @Test
    void healthyIdleStateReportsNoProblems() {
        BaritoneAdvice advice = BaritoneAssistant.advise(
                status(false, false, false, false, false, true), history(), "", false);
        assertTrue(advice.available());
        assertTrue(advice.likelyIssues().stream().anyMatch(i -> i.contains("No problems")));
        assertTrue(advice.suggestions().stream().anyMatch(s -> s.toLowerCase().contains("no action")));
    }

    @Test
    void repeatedErrorsInHistoryAreSurfaced() {
        BaritoneHistory history = history();
        history.error("boom one", 1_000);
        history.error("boom two", 2_000);
        BaritoneAdvice advice = BaritoneAssistant.advise(
                status(false, false, false, false, false, true), history, "", false);
        assertTrue(advice.likelyIssues().stream().anyMatch(i -> i.contains("Multiple recent")));
    }

    @Test
    void pausedNavigationIsReportedInTheSummary() {
        BaritoneAdvice advice = BaritoneAssistant.advise(
                status(true, false, false, false, true, true), history(), "", false);
        assertTrue(advice.summary().toLowerCase().contains("paused"));
    }

    @Test
    void reportSummaryNeverLeaksCoordinates() {
        BaritoneStatus withCoords = status(true, true, false, true, false, true);
        String summary = BaritoneAssistant.reportSummary(withCoords, "timeout");
        assertTrue(summary.contains("GoalBlock"), "the goal type is safe to report");
        assertFalse(summary.contains("10 64 -20"), "the goal detail must not be reported");
        assertTrue(summary.contains("timeout"));
    }

    @Test
    void reportSummaryHandlesUnavailable() {
        assertEquals("Baritone unavailable",
                BaritoneAssistant.reportSummary(BaritoneStatus.unavailable("x"), ""));
    }
}
