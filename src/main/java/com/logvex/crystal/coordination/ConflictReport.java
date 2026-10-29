package com.logvex.crystal.coordination;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of inspecting the client for module-coordination problems.
 *
 * <p>One issue list, each entry carrying a severity, a category and a plain explanation, so
 * the GUI, the command surface and diagnostics all render the same facts. The report is a pure
 * value: it never mutates anything and never resolves a conflict by itself.
 */
public final class ConflictReport {

    /** How serious an issue is. */
    public enum Severity {
        /** The affected module cannot operate at all. */
        BLOCKING,
        /** The client works but behaviour is ambiguous or wasteful. */
        WARNING,
        /** Worth knowing; no functional impact. */
        INFO
    }

    /** The kind of coordination problem. */
    public enum Category {
        MISSING_DEPENDENCY,
        MISSING_INTEGRATION,
        CIRCULAR_DEPENDENCY,
        ACTIVE_MODULE_CONFLICT,
        KEYBIND_CONFLICT,
        ACTION_CONTENTION
    }

    /** One finding. */
    public record Issue(Severity severity, Category category, String module, String message) {

        public String describe() {
            String who = module == null || module.isBlank() ? "" : module + ": ";
            return "[" + severity + "/" + category + "] " + who + message;
        }
    }

    private final List<Issue> issues = new ArrayList<>();

    public void add(Severity severity, Category category, String module, String message) {
        issues.add(new Issue(severity, category, module, message));
    }

    public List<Issue> issues() {
        return List.copyOf(issues);
    }

    public boolean clean() {
        return issues.isEmpty();
    }

    public long count(Severity severity) {
        return issues.stream().filter(issue -> issue.severity() == severity).count();
    }

    /** Only the blocking issues, for a compact "why can't this run" answer. */
    public List<Issue> blocking() {
        List<Issue> result = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.severity() == Severity.BLOCKING) {
                result.add(issue);
            }
        }
        return result;
    }

    /** A one-line summary for the diagnostics HUD. */
    public String summary() {
        if (issues.isEmpty()) {
            return "no conflicts";
        }
        return issues.size() + " issue(s): " + count(Severity.BLOCKING) + " blocking, "
                + count(Severity.WARNING) + " warning";
    }
}
