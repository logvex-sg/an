package com.logvex.crystal.scan;

/**
 * Formats a {@link ChestReport} for local display. Kept separate from the module so the
 * exact text that reaches the HUD/chat is unit-testable without a client.
 */
public final class ReportPresenter {

    private ReportPresenter() {
    }

    /** The line shown in the local notification and written to the client log. */
    public static String notificationText(ChestReport report) {
        return "\u2661 chest stash: " + report.summary();
    }

    /** The structured line written to the client log. */
    public static String logLine(ChestReport report) {
        return "ChestLogger: " + report.summary() + " [" + report.id() + "]";
    }
}
