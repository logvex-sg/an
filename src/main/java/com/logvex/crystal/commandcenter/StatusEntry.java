package com.logvex.crystal.commandcenter;

/**
 * One fact the Command Center reports: a label, a value, and how healthy it is.
 *
 * <p>Entries are the atoms every section is built from. Keeping them a plain value type
 * means the model is Minecraft-independent and a provider can be unit tested without a
 * game. {@code detail} is an optional second line (a fix, a path, a reason) shown only when
 * there is something useful to add.
 */
public record StatusEntry(String label, String value, HealthStatus status, String detail) {

    public StatusEntry {
        label = label == null ? "" : label;
        value = value == null ? "" : value;
        status = status == null ? HealthStatus.OK : status;
        detail = detail == null ? "" : detail;
    }

    public static StatusEntry ok(String label, String value) {
        return new StatusEntry(label, value, HealthStatus.OK, "");
    }

    public static StatusEntry info(String label, String value) {
        return new StatusEntry(label, value, HealthStatus.INFO, "");
    }

    public static StatusEntry attention(String label, String value, String detail) {
        return new StatusEntry(label, value, HealthStatus.ATTENTION, detail);
    }

    public static StatusEntry critical(String label, String value, String detail) {
        return new StatusEntry(label, value, HealthStatus.CRITICAL, detail);
    }

    public boolean hasDetail() {
        return !detail.isBlank();
    }

    /** A single "Label: value" line, or "Label: value — detail" when a detail exists. */
    public String describe() {
        String base = label + ": " + value;
        return hasDetail() ? base + " \u2014 " + detail : base;
    }
}
