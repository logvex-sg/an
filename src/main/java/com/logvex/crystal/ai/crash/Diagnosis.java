package com.logvex.crystal.ai.crash;

import java.util.List;

/**
 * The result of analysing one crash. It deliberately separates confirmed facts from
 * hypotheses so the UI never presents a guess as a cause: {@link #facts} are things read
 * directly from the record, {@link #hypotheses} are rule-derived possibilities, and
 * {@link #confidence} describes the strongest claim.
 */
public record Diagnosis(
        String fingerprint,
        CrashCategory category,
        CrashSeverity severity,
        Confidence confidence,
        String likelyCause,
        List<String> facts,
        List<String> hypotheses,
        List<String> suggestedFixes,
        String suspectedModule,
        List<String> keyFrames,
        boolean aiGenerated) {

    /** How strongly the analyzer stands behind its explanation. */
    public enum Confidence {
        CONFIRMED("Confirmed"),
        LIKELY("Likely"),
        POSSIBLE("Possible"),
        UNKNOWN("Unknown");

        private final String label;

        Confidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public Diagnosis {
        facts = List.copyOf(facts);
        hypotheses = List.copyOf(hypotheses);
        suggestedFixes = List.copyOf(suggestedFixes);
        keyFrames = List.copyOf(keyFrames);
    }
}
