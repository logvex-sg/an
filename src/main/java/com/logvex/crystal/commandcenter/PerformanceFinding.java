package com.logvex.crystal.commandcenter;

/**
 * One thing the performance intelligence noticed, with a concrete suggestion.
 *
 * <p>A finding is deliberately advisory: it names the observation, the severity, and what
 * the user could do, but it never changes a setting by itself. {@code metric} is the value
 * that triggered the rule, so the UI can show "fps 38" rather than just "low fps".
 */
public record PerformanceFinding(String rule, HealthStatus severity, String observation,
                                 String suggestion, String metric) {

    public PerformanceFinding {
        rule = rule == null ? "" : rule;
        severity = severity == null ? HealthStatus.INFO : severity;
        observation = observation == null ? "" : observation;
        suggestion = suggestion == null ? "" : suggestion;
        metric = metric == null ? "" : metric;
    }

    /** A one-line description combining the observation and the suggestion. */
    public String describe() {
        return suggestion.isBlank() ? observation : observation + " \u2014 " + suggestion;
    }
}
