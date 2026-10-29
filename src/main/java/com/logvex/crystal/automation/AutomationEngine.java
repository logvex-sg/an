package com.logvex.crystal.automation;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.ClientEventBus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The deterministic rule engine: it evaluates {@link AutomationRule}s once per tick against an
 * {@link AutomationContext} and, when a rule's trigger and conditions hold, runs its actions.
 *
 * <p>Guards that keep automation safe and predictable:
 * <ul>
 *   <li><b>Global enable</b> - nothing runs until {@link #setEnabled(boolean)} is true.</li>
 *   <li><b>Per-rule cooldown</b> - a rule cannot fire more often than its cooldown, so an
 *       "every tick" trigger is bounded.</li>
 *   <li><b>Global rate limit</b> - a hard ceiling on firings per minute, so a mistake in the
 *       rule set cannot flood the client.</li>
 *   <li><b>World requirement</b> - rules marked {@code requiresWorld} are skipped outside a
 *       loaded world.</li>
 *   <li><b>Bounded history</b> - the firing log is a fixed-size ring.</li>
 * </ul>
 *
 * <p>Minecraft-independent: the context and clock are injected, so every guard is unit tested.
 */
public final class AutomationEngine {

    /** One record of a rule firing, for the history view and diagnostics. */
    public record Firing(String ruleId, String ruleName, long atMillis, List<String> actions,
                         boolean success, String note) {
        public String describe() {
            return (success ? "\u2713" : "\u2717") + " " + ruleName + " -> "
                    + String.join(", ", actions) + (note == null || note.isBlank() ? "" : " (" + note + ")");
        }
    }

    private static final int HISTORY_CAPACITY = 128;
    private static final long RATE_WINDOW_MILLIS = 60_000L;

    private final List<AutomationRule> rules = new ArrayList<>();
    private final Deque<Firing> history = new ArrayDeque<>();
    private final ClientEventBus bus;
    private final Deque<Long> recentFirings = new ArrayDeque<>();

    private boolean enabled;
    private long maxFiringsPerMinute = 120L;
    private long suppressedByRateLimit;
    private long suppressedByCooldown;

    public AutomationEngine(ClientEventBus bus) {
        this.bus = bus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public long maxFiringsPerMinute() {
        return maxFiringsPerMinute;
    }

    public void setMaxFiringsPerMinute(long value) {
        this.maxFiringsPerMinute = Math.max(1, value);
    }

    public void addRule(AutomationRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /** Adds or replaces a rule with the same id. */
    public void upsertRule(AutomationRule rule) {
        if (rule == null) {
            return;
        }
        rules.removeIf(existing -> existing.id().equals(rule.id()));
        rules.add(rule);
    }

    public boolean removeRule(String id) {
        return rules.removeIf(rule -> rule.id().equals(id));
    }

    public Optional<AutomationRule> byId(String id) {
        return rules.stream().filter(rule -> rule.id().equals(id)).findFirst();
    }

    public List<AutomationRule> rules() {
        return List.copyOf(rules);
    }

    public void clearRules() {
        rules.clear();
    }

    public List<Firing> history() {
        return new ArrayList<>(history);
    }

    public void clearHistory() {
        history.clear();
    }

    public long suppressedByRateLimit() {
        return suppressedByRateLimit;
    }

    public long suppressedByCooldown() {
        return suppressedByCooldown;
    }

    public int ruleCount() {
        return rules.size();
    }

    /**
     * Evaluates every rule once. Call on the client thread each tick.
     *
     * @return the number of rules that fired this tick
     */
    public int tick(AutomationContext context, long now) {
        if (!enabled || context == null) {
            return 0;
        }
        int fired = 0;
        for (AutomationRule rule : rules) {
            try {
                if (!rule.runnable()) {
                    continue;
                }
                if (rule.requiresWorld() && !context.worldAvailable()) {
                    continue;
                }
                if (!rule.cooldownElapsed(now)) {
                    suppressedByCooldown++;
                    continue;
                }
                if (!triggerHolds(rule, context, now)) {
                    continue;
                }
                if (!conditionsHold(rule, context)) {
                    continue;
                }
                if (rateLimited(now)) {
                    suppressedByRateLimit++;
                    continue;
                }
                fire(rule, context, now);
                fired++;
            } finally {
                // Recorded last so an event trigger's baseline is the previous evaluation,
                // not this one - otherwise an event that arrived this tick would be missed.
                rule.markEvaluated(now);
            }
        }
        return fired;
    }

    private boolean conditionsHold(AutomationRule rule, AutomationContext context) {
        for (Condition condition : rule.conditions()) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    private boolean triggerHolds(AutomationRule rule, AutomationContext context, long now) {
        Trigger trigger = rule.trigger();
        if (trigger instanceof Trigger.Interval interval) {
            return !rule.hasFired() || now - rule.lastFiredMillis() >= interval.intervalMillis();
        }
        if (trigger instanceof Trigger.Always) {
            return true;
        }
        if (trigger instanceof Trigger.OnEvent onEvent) {
            return eventSeenSince(onEvent.kind(), rule.lastEvaluatedMillis());
        }
        if (trigger instanceof Trigger.LowHealth lowHealth) {
            return context.healthFraction() <= lowHealth.fraction();
        }
        if (trigger instanceof Trigger.TargetNear near) {
            double distance = context.targetDistance();
            return distance >= 0 && distance <= near.distance();
        }
        if (trigger instanceof Trigger.TargetLost) {
            return context.targetDistance() < 0;
        }
        if (trigger instanceof Trigger.IntegrationAvailable integration) {
            return context.integrationAvailable(integration.name());
        }
        if (trigger instanceof Trigger.ModuleEnabled moduleEnabled) {
            return context.moduleEnabled(moduleEnabled.name());
        }
        if (trigger instanceof Trigger.ProfileActive profileActive) {
            return profileActive.name().equalsIgnoreCase(context.activeProfile());
        }
        return false;
    }

    private boolean eventSeenSince(com.logvex.crystal.event.EventKind kind, long sinceMillis) {
        if (bus == null) {
            return false;
        }
        for (ClientEvent event : bus.history(kind)) {
            if (event.atMillis() > sinceMillis) {
                return true;
            }
        }
        return false;
    }

    private boolean rateLimited(long now) {
        while (!recentFirings.isEmpty() && now - recentFirings.peekFirst() > RATE_WINDOW_MILLIS) {
            recentFirings.removeFirst();
        }
        return recentFirings.size() >= maxFiringsPerMinute;
    }

    private void fire(AutomationRule rule, AutomationContext context, long now) {
        List<String> performed = new ArrayList<>();
        boolean success = true;
        String note = "";
        for (Action action : rule.actions()) {
            boolean ok;
            try {
                ok = action.execute(context);
            } catch (RuntimeException e) {
                ok = false;
                note = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            performed.add(action.describe());
            if (!ok) {
                success = false;
                if (note.isEmpty()) {
                    note = "action did not take effect";
                }
            }
        }
        rule.markFired(now);
        recentFirings.addLast(now);
        Firing firing = new Firing(rule.id(), rule.name(), now, List.copyOf(performed), success, note);
        history.addLast(firing);
        while (history.size() > HISTORY_CAPACITY) {
            history.removeFirst();
        }
    }

    /** A one-line summary for diagnostics. */
    public String summary() {
        long runnable = rules.stream().filter(AutomationRule::runnable).count();
        return "automation: " + (enabled ? "on" : "off") + ", " + runnable + "/" + rules.size()
                + " runnable, " + history.size() + " recent firing(s)";
    }
}
