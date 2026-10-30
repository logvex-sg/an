package com.logvex.crystal.commandcenter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable snapshot of the whole client as the Command Center sees it: one
 * {@link StatusSection} per subsystem, plus the rolled-up overall health.
 *
 * <p>It is a pure value built from already-computed state, so it can be handed to the
 * command surface, the ClickGUI, the AI context builder and the tests without any of them
 * touching the live services. Sections keep insertion order, and a lookup by id is O(1),
 * so a caller that wants just the errors section does not scan.
 */
public final class ClientStatusModel {

    private final List<StatusSection> sections;
    private final Map<String, StatusSection> byId;
    private final HealthStatus overall;
    private final long capturedAtMillis;

    public ClientStatusModel(List<StatusSection> sections, long capturedAtMillis) {
        List<StatusSection> copy = sections == null ? List.of() : List.copyOf(sections);
        this.sections = copy;
        this.capturedAtMillis = capturedAtMillis;
        Map<String, StatusSection> index = new LinkedHashMap<>();
        HealthStatus worst = HealthStatus.OK;
        for (StatusSection section : copy) {
            index.put(section.id(), section);
            worst = worst.worseOf(section.worst());
        }
        this.byId = index;
        this.overall = worst;
    }

    public List<StatusSection> sections() {
        return sections;
    }

    public StatusSection section(String id) {
        return id == null ? null : byId.get(id);
    }

    /** The worst status across every section. */
    public HealthStatus overall() {
        return overall;
    }

    public long capturedAtMillis() {
        return capturedAtMillis;
    }

    /** Every entry that needs attention, across all sections, in section order. */
    public List<StatusEntry> problems() {
        List<StatusEntry> result = new ArrayList<>();
        for (StatusSection section : sections) {
            result.addAll(section.problems());
        }
        return result;
    }

    public int problemCount() {
        return problems().size();
    }

    /** True when nothing in any section needs attention. */
    public boolean healthy() {
        return overall == HealthStatus.OK;
    }

    /** A one-line roll-up, e.g. {@code "3 problems across 9 sections (1 critical)"}. */
    public String summary() {
        if (sections.isEmpty()) {
            return "no status available";
        }
        int problems = problemCount();
        String head = problems == 0
                ? "all clear"
                : problems + " problem" + (problems == 1 ? "" : "s") + " across " + sections.size() + " sections";
        long critical = sections.stream().filter(s -> s.worst() == HealthStatus.CRITICAL).count();
        return critical > 0 ? head + " (" + critical + " critical)" : head;
    }
}
