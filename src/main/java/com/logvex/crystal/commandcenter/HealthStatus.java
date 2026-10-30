package com.logvex.crystal.commandcenter;

/**
 * The coarse health of one thing the Command Center inspects.
 *
 * <p>Ordered from best to worst so a section can report its worst entry with a single
 * comparison ({@link #worseOf}), and so the whole client rolls up to one colour. The
 * meaning is deliberately small: {@code OK} needs no attention, {@code INFO} is worth
 * knowing, {@code ATTENTION} is a degraded state the user should look at, and
 * {@code CRITICAL} is a fault that is actively breaking something.
 */
public enum HealthStatus {

    OK("OK", 0xFF8FE3A8),
    INFO("Info", 0xFFA0D8FF),
    ATTENTION("Attention", 0xFFFFC46B),
    CRITICAL("Critical", 0xFFFF6B85);

    private final String label;
    private final int tint;

    HealthStatus(String label, int tint) {
        this.label = label;
        this.tint = tint;
    }

    public String label() {
        return label;
    }

    /** Pastel fallback colour used when no theme palette is available. */
    public int tint() {
        return tint;
    }

    /** True when this status is anything other than {@link #OK}. */
    public boolean needsAttention() {
        return this != OK;
    }

    /** The more severe of two statuses. */
    public HealthStatus worseOf(HealthStatus other) {
        if (other == null) {
            return this;
        }
        return other.ordinal() > this.ordinal() ? other : this;
    }
}
