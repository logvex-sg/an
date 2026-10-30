package com.logvex.crystal.ai.baritone;

import com.logvex.crystal.integration.baritone.BaritoneHistory;
import com.logvex.crystal.integration.baritone.BaritoneStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic Baritone diagnostics. Reads the live {@link BaritoneStatus} and the bounded
 * {@link BaritoneHistory} and produces guidance that is always available — no model required.
 *
 * <p>The rules describe states a navigator commonly hits (no path, path stuck, cancellation not
 * safe, repeated errors, goal never reached) and phrase each as a possibility, with a concrete
 * action. This keeps the assistant useful and honest even with the local model absent.
 */
public final class BaritoneAssistant {

    private BaritoneAssistant() {
    }

    public static BaritoneAdvice advise(BaritoneStatus status, BaritoneHistory history,
                                        String lastActionError, boolean paused) {
        if (status == null || !status.available()) {
            String reason = status == null ? "no status" : status.reason();
            return BaritoneAdvice.unavailable(reason == null || reason.isBlank() ? "not detected" : reason);
        }

        List<String> facts = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        facts.add("Version: " + status.version());
        facts.add(status.statusText());
        if (status.hasGoal()) {
            facts.add("Goal: " + status.goalType() + " " + status.goalDetail());
        } else {
            facts.add("No goal is currently set.");
        }
        if (status.pathing() || status.calculating() || status.hasPath()) {
            facts.add("Path: " + (status.calculating() ? "calculating"
                    : status.hasPath() ? status.pathLength() + " nodes" : "none")
                    + (status.ticksToGoal() >= 0 ? ", ~" + status.ticksToGoal() + " ticks to goal" : ""));
        }
        if (status.paused() || paused) {
            facts.add("Navigation is paused.");
        }
        if (lastActionError != null && !lastActionError.isBlank()) {
            facts.add("Last integration error: " + lastActionError);
        }

        // --- rule: goal set but no path and not calculating ---
        if (status.hasGoal() && !status.hasPath() && !status.calculating()) {
            issues.add("A goal is set but no path exists yet — the goal may be unreachable.");
            suggestions.add("Wait a moment for path calculation; if it stays empty, pick a closer goal.");
        }

        // --- rule: pathing without progress ---
        if (status.pathing() && status.ticksToGoal() < 0) {
            issues.add("Baritone reports pathing but not an estimated time — it may be stuck.");
            suggestions.add("If progress has stopped, cancel and re-issue the goal from a clear position.");
        }

        // --- rule: goal set, not pathing, not calculating, not paused ---
        if (status.hasGoal() && !status.pathing() && !status.calculating() && !status.paused() && !paused) {
            issues.add("Baritone is idle with a goal set — navigation may not have started.");
            suggestions.add("Use the resume command to continue the current goal.");
        }

        // --- rule: cancellation not safe ---
        if (status.hasGoal() && !status.safeToCancel()) {
            issues.add("Baritone reports it is not currently safe to cancel.");
            suggestions.add("Wait until the current movement step finishes before cancelling.");
        }

        // --- rule: repeated integration errors ---
        if (lastActionError != null && !lastActionError.isBlank()) {
            issues.add("The last Baritone action failed: " + lastActionError);
            suggestions.add("Check that the Baritone version matches this Minecraft release.");
        }

        // --- rule: recent error history ---
        if (history != null) {
            int recentErrors = 0;
            for (BaritoneHistory.Entry entry : history.recent(16)) {
                if ("error".equals(entry.kind())) {
                    recentErrors++;
                }
            }
            if (recentErrors >= 2) {
                issues.add("Multiple recent integration errors (" + recentErrors + ") were recorded.");
                suggestions.add("Review the Baritone history and reinstall Baritone if errors continue.");
            }
        }

        if (issues.isEmpty()) {
            issues.add("No problems detected in the current navigation state.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Baritone looks healthy; no action needed.");
        }

        String summary = buildSummary(status, paused);
        return new BaritoneAdvice(true, status.version(), summary, facts, issues, suggestions, false);
    }

    private static String buildSummary(BaritoneStatus status, boolean paused) {
        if (status.paused() || paused) {
            return "Navigation is paused" + (status.hasGoal() ? " with a goal set." : ".");
        }
        if (status.calculating()) {
            return "Baritone is calculating a path.";
        }
        if (status.pathing()) {
            return "Baritone is navigating to " + (status.goalDetail().isBlank() ? "its goal" : status.goalDetail()) + ".";
        }
        if (status.hasGoal()) {
            return "A goal is set but Baritone is not currently moving.";
        }
        return "Baritone is idle and available.";
    }

    /**
     * A compact, privacy-safe summary of Baritone state for a report or an AI prompt. Coordinates
     * are described by type only ("GoalBlock"), never as numbers, so nothing location-revealing
     * leaves the machine.
     */
    public static String reportSummary(BaritoneStatus status, String lastActionError) {
        if (status == null || !status.available()) {
            return "Baritone unavailable";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("version=").append(status.version())
                .append(", goal=").append(status.hasGoal() ? status.goalType() : "none")
                .append(", pathing=").append(status.pathing())
                .append(", calculating=").append(status.calculating())
                .append(", paused=").append(status.paused());
        if (lastActionError != null && !lastActionError.isBlank()) {
            summary.append(", lastError=").append(lastActionError.toLowerCase(Locale.ROOT).trim());
        }
        return summary.toString();
    }
}
