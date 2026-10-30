package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.automation.AutomationEngine;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the automation engine: whether it is on, how many rules exist, and how the last
 * run went.
 *
 * <p>Read-only over the {@link AutomationEngine}. Rate-limit and cooldown suppressions are
 * surfaced as {@code Info}, because they are the engine working as designed, not a fault.
 */
public final class AutomationStatusProvider implements StatusProvider {

    private final AutomationEngine automation;

    public AutomationStatusProvider(AutomationEngine automation) {
        this.automation = automation;
    }

    @Override
    public String id() {
        return "automation";
    }

    @Override
    public String title() {
        return "Automation";
    }

    @Override
    public StatusSection snapshot() {
        if (automation == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Automation", "unavailable")));
        }
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(automation.isEnabled() ? StatusEntry.ok("Enabled", "on") : StatusEntry.info("Enabled", "off"));
        entries.add(StatusEntry.ok("Rules", String.valueOf(automation.ruleCount())));
        entries.add(StatusEntry.ok("Firings", String.valueOf(automation.history().size())));
        long suppressed = automation.suppressedByRateLimit() + automation.suppressedByCooldown();
        if (suppressed > 0) {
            entries.add(StatusEntry.info("Suppressed", suppressed + " (rate limit / cooldown)"));
        }
        String summary = (automation.isEnabled() ? "enabled" : "disabled")
                + ", " + automation.ruleCount() + " rule(s)";
        return new StatusSection(id(), title(), summary, entries);
    }
}
