package com.logvex.crystal.ai.baritone;

import java.util.List;

/**
 * The result of reasoning about Baritone's current state. Facts are read from the live
 * {@code BaritoneStatus}; issues and suggestions come from deterministic rules and are labelled
 * as such, so a suggestion is never presented as a confirmed fault.
 */
public record BaritoneAdvice(
        boolean available,
        String version,
        String summary,
        List<String> facts,
        List<String> likelyIssues,
        List<String> suggestions,
        boolean aiGenerated) {

    public BaritoneAdvice {
        facts = List.copyOf(facts);
        likelyIssues = List.copyOf(likelyIssues);
        suggestions = List.copyOf(suggestions);
    }

    public static BaritoneAdvice unavailable(String reason) {
        return new BaritoneAdvice(false, "unknown", "Baritone is not available: " + reason,
                List.of("Integration status: unavailable (" + reason + ")"),
                List.of("Baritone is not installed or its API did not respond."),
                List.of("Install the Baritone build for this Minecraft version and restart.",
                        "Check the Baritone Assistant after restart to confirm it is detected."),
                false);
    }
}
