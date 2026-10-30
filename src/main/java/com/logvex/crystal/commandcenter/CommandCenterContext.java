package com.logvex.crystal.commandcenter;

import java.util.List;

/**
 * Turns the Command Center's structured state into a short, bounded, privacy-safe block of
 * text that can be handed to the optional local model.
 *
 * <p>This is the "structured context" the AI integration consumes: rather than dumping logs,
 * it summarises the rolled-up health, the problems that need attention, the performance
 * verdict and the module-compatibility roll-up. Nothing sensitive is included - no server
 * address, no world seed, no tokens - because every value here already came from the client's
 * own, non-sensitive status model.
 *
 * <p>Pure and Minecraft-independent, so the exact text is unit testable and auditable.
 */
public final class CommandCenterContext {

    /** Keeps the block small so a 0.5B model stays fast and on-topic. */
    static final int MAX_CHARS = 1400;
    /** Caps how many individual problems are listed, so a broken client cannot flood it. */
    static final int MAX_PROBLEMS = 12;

    private CommandCenterContext() {
    }

    /** A compact multi-line summary of {@code model} and {@code performance}. */
    public static String build(ClientStatusModel model, PerformanceReport performance) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("Client status: ");
        sb.append(model == null ? "unavailable" : model.summary()).append('\n');
        if (model != null) {
            for (StatusSection section : model.sections()) {
                if (!section.worst().needsAttention()) {
                    continue;
                }
                sb.append("- ").append(section.title()).append(" (").append(section.worst().label()).append(')');
                if (!section.summary().isBlank()) {
                    sb.append(": ").append(section.summary());
                }
                sb.append('\n');
            }
            List<StatusEntry> problems = model.problems();
            int shown = 0;
            for (StatusEntry problem : problems) {
                if (shown++ >= MAX_PROBLEMS) {
                    sb.append("- \u2026 and ").append(problems.size() - MAX_PROBLEMS).append(" more\n");
                    break;
                }
                sb.append("  \u2022 ").append(problem.describe()).append('\n');
            }
        }
        if (performance != null) {
            sb.append("Performance: ").append(performance.summary()).append('\n');
            for (PerformanceFinding finding : performance.findings()) {
                sb.append("  \u2022 [").append(finding.severity().label()).append("] ")
                        .append(finding.describe()).append('\n');
            }
        }
        return cap(sb.toString());
    }

    private static String cap(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS);
    }
}
