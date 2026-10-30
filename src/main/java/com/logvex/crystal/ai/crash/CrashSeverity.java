package com.logvex.crystal.ai.crash;

/** How damaging a captured failure is, used to sort the crash history and pick a glyph. */
public enum CrashSeverity {

    LOW("Low", 0xFFA0D8FF),
    MEDIUM("Medium", 0xFFFFC46B),
    HIGH("High", 0xFFFF9E6B),
    CRITICAL("Critical", 0xFFFF6B85);

    private final String label;
    private final int tint;

    CrashSeverity(String label, int tint) {
        this.label = label;
        this.tint = tint;
    }

    public String label() {
        return label;
    }

    /** Pastel fallback tint used when no palette is available. */
    public int tint() {
        return tint;
    }
}
