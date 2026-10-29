package com.logvex.crystal.automation;

import java.util.ArrayList;
import java.util.List;

/**
 * One automation rule: an optional trigger, zero or more conditions, and one or more actions.
 *
 * <p>Deliberately data, not code. A rule is created, edited, saved and reloaded as plain
 * values; the {@link AutomationEngine} interprets it. This is what makes automation safe and
 * auditable: the worst a malformed rule can do is fail to parse.
 *
 * <p>A rule may be disabled without being deleted, and carries a per-rule cooldown so a rule
 * that fires on every tick cannot act more often than intended.
 */
public final class AutomationRule {

    private String id;
    private String name;
    private boolean enabled = true;
    private Trigger trigger;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private long cooldownMillis = 1000L;
    /** Run the rule only while a world is loaded. */
    private boolean requiresWorld = true;

    // Runtime bookkeeping (not persisted).
    private long lastFiredMillis;
    private long fireCount;
    private long lastEvaluatedMillis;
    private boolean hasFired;

    public AutomationRule(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public AutomationRule id(String value) {
        this.id = value;
        return this;
    }

    public String name() {
        return name;
    }

    public AutomationRule name(String value) {
        this.name = value;
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public AutomationRule enabled(boolean value) {
        this.enabled = value;
        return this;
    }

    public Trigger trigger() {
        return trigger;
    }

    public AutomationRule trigger(Trigger value) {
        this.trigger = value;
        return this;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    public AutomationRule addCondition(Condition condition) {
        if (condition != null) {
            conditions.add(condition);
        }
        return this;
    }

    public List<Action> actions() {
        return actions;
    }

    public AutomationRule addAction(Action action) {
        if (action != null) {
            actions.add(action);
        }
        return this;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public AutomationRule cooldownMillis(long value) {
        this.cooldownMillis = Math.max(0, value);
        return this;
    }

    public boolean requiresWorld() {
        return requiresWorld;
    }

    public AutomationRule requiresWorld(boolean value) {
        this.requiresWorld = value;
        return this;
    }

    public long lastFiredMillis() {
        return lastFiredMillis;
    }

    public long fireCount() {
        return fireCount;
    }

    public long lastEvaluatedMillis() {
        return lastEvaluatedMillis;
    }

    /** True when this rule is complete enough to run. */
    public boolean runnable() {
        return enabled && trigger != null && !actions.isEmpty();
    }

    /** A one-line description for the command surface and editor. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(enabled ? "[on] " : "[off] ").append(name);
        builder.append(" :: ").append(trigger == null ? "no trigger" : trigger.describe());
        if (!conditions.isEmpty()) {
            builder.append(" when ");
            for (int i = 0; i < conditions.size(); i++) {
                builder.append(i == 0 ? "" : " and ").append(conditions.get(i).describe());
            }
        }
        builder.append(" -> ");
        for (int i = 0; i < actions.size(); i++) {
            builder.append(i == 0 ? "" : ", ").append(actions.get(i).describe());
        }
        return builder.toString();
    }

    /** Called by the engine after the rule fires, to advance its cooldown and counters. */
    void markFired(long now) {
        lastFiredMillis = now;
        fireCount++;
        hasFired = true;
    }

    void markEvaluated(long now) {
        lastEvaluatedMillis = now;
    }

    /** True when this rule has fired at least once since it was loaded. */
    boolean hasFired() {
        return hasFired;
    }

    /**
     * True when the per-rule cooldown has elapsed. A rule that has never fired is always
     * eligible, so the first opportunity is not silently skipped by an artificial delay.
     */
    boolean cooldownElapsed(long now) {
        return !hasFired || now - lastFiredMillis >= cooldownMillis;
    }
}
