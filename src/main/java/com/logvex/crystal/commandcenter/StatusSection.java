package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.List;

/**
 * A named group of {@link StatusEntry} values: one subsystem's answer to "what is my
 * state right now".
 *
 * <p>A section is a pure value. It is built by a {@link StatusProvider}, carries a stable
 * id, a display title, the entries and an optional one-line summary. The section rolls its
 * own worst status up from its entries, so the Command Center never has to know how a
 * particular subsystem judges severity.
 */
public final class StatusSection {

    private final String id;
    private final String title;
    private final String summary;
    private final List<StatusEntry> entries;

    public StatusSection(String id, String title, String summary, List<StatusEntry> entries) {
        this.id = id == null ? "" : id;
        this.title = title == null ? id : title;
        this.summary = summary == null ? "" : summary;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static StatusSection of(String id, String title, StatusEntry... entries) {
        return new StatusSection(id, title, "", List.of(entries));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public List<StatusEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The worst status among the entries, or {@link HealthStatus#OK} when empty. */
    public HealthStatus worst() {
        HealthStatus worst = HealthStatus.OK;
        for (StatusEntry entry : entries) {
            worst = worst.worseOf(entry.status());
        }
        return worst;
    }

    /** The entries whose status needs attention, in declaration order. */
    public List<StatusEntry> problems() {
        List<StatusEntry> result = new ArrayList<>();
        for (StatusEntry entry : entries) {
            if (entry.status().needsAttention()) {
                result.add(entry);
            }
        }
        return result;
    }

    /** How many entries need attention. */
    public int problemCount() {
        return problems().size();
    }
}
