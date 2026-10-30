package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.event.ClientEventBus;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the shared event stream: how many events have been published, how many were
 * deduplicated away, how many subscribers are attached and what is retained for late readers.
 *
 * <p>The bus already applies the anti-storm rules, so this section is about visibility: a
 * growing suppressed count is healthy (dedup working), a growing retained backlog with no
 * subscribers is not.
 */
public final class EventStatusProvider implements StatusProvider {

    private final ClientEventBus events;

    public EventStatusProvider(ClientEventBus events) {
        this.events = events;
    }

    @Override
    public String id() {
        return "events";
    }

    @Override
    public String title() {
        return "Events";
    }

    @Override
    public StatusSection snapshot() {
        if (events == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Events", "unavailable")));
        }
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(StatusEntry.ok("Published", String.valueOf(events.publishedTotal())));
        entries.add(StatusEntry.ok("Deduplicated", String.valueOf(events.suppressedTotal())));
        entries.add(StatusEntry.ok("Subscribers", String.valueOf(events.subscriberCount())));
        int retained = events.history().size();
        entries.add(new StatusEntry("Retained", String.valueOf(retained),
                retained > 0 && events.subscriberCount() == 0
                        ? com.logvex.crystal.commandcenter.HealthStatus.INFO
                        : com.logvex.crystal.commandcenter.HealthStatus.OK, ""));
        return new StatusSection(id(), title(), events.summary(), entries);
    }
}
