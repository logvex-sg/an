package com.logvex.crystal.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.event.ClientEvent;
import com.logvex.crystal.event.ClientEventBus;
import com.logvex.crystal.event.EventKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers triggers, conditions, guards (cooldown, rate limit, world) and verified actions. */
class AutomationEngineTest {

    /** A fully controllable context; every effect is recorded. */
    private static final class FakeContext implements AutomationContext {
        boolean world = true;
        double health = 1.0;
        double targetDistance = -1;
        boolean idle = true;
        String profile = "";
        final Set<String> enabled = new LinkedHashSet<>();
        final Set<String> installed = new LinkedHashSet<>();
        final List<String> log = new ArrayList<>();
        final List<String> notifications = new ArrayList<>();
        boolean enableSucceeds = true;
        boolean cancelRouteResult = true;
        boolean throwOnNotify;

        @Override
        public boolean playerAvailable() {
            return world;
        }

        @Override
        public boolean worldAvailable() {
            return world;
        }

        @Override
        public boolean moduleInstalled(String name) {
            return installed.contains(name);
        }

        @Override
        public boolean moduleEnabled(String name) {
            return enabled.contains(name);
        }

        @Override
        public boolean integrationAvailable(String name) {
            return false;
        }

        @Override
        public String activeProfile() {
            return profile;
        }

        @Override
        public double healthFraction() {
            return health;
        }

        @Override
        public double targetDistance() {
            return targetDistance;
        }

        @Override
        public boolean actionIdle() {
            return idle;
        }

        @Override
        public boolean enableModule(String name) {
            if (enableSucceeds) {
                enabled.add(name);
            }
            return enableSucceeds;
        }

        @Override
        public boolean disableModule(String name) {
            enabled.remove(name);
            return true;
        }

        @Override
        public boolean switchProfile(String name) {
            profile = name;
            return true;
        }

        @Override
        public boolean cancelRoute() {
            return cancelRouteResult;
        }

        @Override
        public void notify(String message, String level) {
            if (throwOnNotify) {
                throw new IllegalStateException("nope");
            }
            notifications.add(level + ":" + message);
        }

        @Override
        public void log(String message) {
            log.add(message);
        }
    }

    private static AutomationRule rule(String name, Trigger trigger, Action... actions) {
        AutomationRule rule = new AutomationRule("id-" + name, name).trigger(trigger).cooldownMillis(0);
        for (Action action : actions) {
            rule.addAction(action);
        }
        return rule;
    }

    @Test
    void nothingFiresWhileEngineDisabled() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.addRule(rule("r", new Trigger.Always(), new Action.Log("x")));
        FakeContext context = new FakeContext();
        assertEquals(0, engine.tick(context, 0));
        assertTrue(context.log.isEmpty());
    }

    @Test
    void alwaysTriggerFiresEachTickWhenEnabled() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.Always(), new Action.Log("tick")));
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        engine.tick(context, 1);
        assertEquals(2, context.log.size());
    }

    @Test
    void intervalTriggerFiresOnlyAfterTheInterval() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.Interval(1000), new Action.Log("x")));
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        engine.tick(context, 500);
        assertEquals(1, context.log.size(), "not yet");
        engine.tick(context, 1000);
        assertEquals(2, context.log.size(), "interval elapsed");
    }

    @Test
    void perRuleCooldownBoundsFiring() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        AutomationRule rule = new AutomationRule("r", "r").trigger(new Trigger.Always())
                .cooldownMillis(1000).addAction(new Action.Log("x"));
        engine.addRule(rule);
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        engine.tick(context, 10);
        assertEquals(1, context.log.size());
        assertTrue(engine.suppressedByCooldown() >= 1);
        engine.tick(context, 1000);
        assertEquals(2, context.log.size());
    }

    @Test
    void conditionsGateTheActions() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        AutomationRule rule = rule("r", new Trigger.Always(), new Action.Log("x"))
                .addCondition(new Condition.Numeric(Condition.Fact.HEALTH,
                        Condition.Comparison.LT, 0.5));
        engine.addRule(rule);
        FakeContext context = new FakeContext();
        context.health = 0.8;
        engine.tick(context, 0);
        assertTrue(context.log.isEmpty(), "condition not met");
        context.health = 0.3;
        engine.tick(context, 1);
        assertEquals(1, context.log.size());
    }

    @Test
    void lowHealthTriggerRespectsThreshold() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.LowHealth(0.25), new Action.Log("low")));
        FakeContext context = new FakeContext();
        context.health = 0.5;
        engine.tick(context, 0);
        assertTrue(context.log.isEmpty());
        context.health = 0.2;
        engine.tick(context, 1);
        assertEquals(1, context.log.size());
    }

    @Test
    void targetNearTriggerNeedsARealTarget() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.TargetNear(4), new Action.Log("engage")));
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        assertTrue(context.log.isEmpty(), "no target -> no fire");
        context.targetDistance = 3;
        engine.tick(context, 1);
        assertEquals(1, context.log.size());
    }

    @Test
    void worldRequirementSkipsRulesOutsideAWorld() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.Always(), new Action.Log("x")));
        FakeContext context = new FakeContext();
        context.world = false;
        engine.tick(context, 0);
        assertTrue(context.log.isEmpty());
    }

    @Test
    void rateLimitCapsFiringsPerMinute() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.setMaxFiringsPerMinute(3);
        engine.addRule(rule("r", new Trigger.Always(), new Action.Log("x")));
        FakeContext context = new FakeContext();
        for (int i = 0; i < 10; i++) {
            engine.tick(context, i);
        }
        assertEquals(3, context.log.size(), "hard cap reached");
        assertTrue(engine.suppressedByRateLimit() >= 1);
    }

    @Test
    void eventTriggerReadsBusHistory() {
        ClientEventBus bus = new ClientEventBus(0);
        AutomationEngine engine = new AutomationEngine(bus);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.OnEvent(EventKind.TARGET_CHANGED), new Action.Log("saw")));
        FakeContext context = new FakeContext();
        engine.tick(context, 100);
        assertTrue(context.log.isEmpty(), "no event yet");
        bus.publish(ClientEvent.of(EventKind.TARGET_CHANGED, 150).put("name", "x").build());
        engine.tick(context, 200);
        assertEquals(1, context.log.size());
    }

    @Test
    void failedActionIsRecordedNotAssumed() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.Always(), new Action.EnableModule("Ghost")));
        FakeContext context = new FakeContext();
        context.enableSucceeds = false;
        engine.tick(context, 0);
        assertEquals(1, engine.history().size());
        assertFalse(engine.history().get(0).success());
    }

    @Test
    void throwingActionIsCaughtAndRecorded() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        // Action is a sealed set of data actions, so the throwing path is exercised through a
        // real action whose context call throws rather than an ad-hoc subclass.
        engine.addRule(rule("r", new Trigger.Always(), new Action.Notify("boom", "info")));
        FakeContext context = new FakeContext();
        context.throwOnNotify = true;
        engine.tick(context, 0);
        assertEquals(1, engine.history().size());
        assertFalse(engine.history().get(0).success());
    }

    @Test
    void historyIsBounded() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.setMaxFiringsPerMinute(100_000);
        engine.addRule(rule("r", new Trigger.Always(), new Action.Log("x")));
        FakeContext context = new FakeContext();
        for (int i = 0; i < 400; i++) {
            engine.tick(context, i);
        }
        assertEquals(128, engine.history().size(), "history ring is bounded");
    }

    @Test
    void incompleteRuleNeverFires() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(new AutomationRule("id", "no-actions").trigger(new Trigger.Always()));
        engine.addRule(new AutomationRule("id2", "no-trigger").addAction(new Action.Log("x")));
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        assertTrue(context.log.isEmpty());
    }

    @Test
    void switchProfileActionGoesThroughContext() {
        AutomationEngine engine = new AutomationEngine(null);
        engine.setEnabled(true);
        engine.addRule(rule("r", new Trigger.Always(), new Action.SwitchProfile("Combat")));
        FakeContext context = new FakeContext();
        engine.tick(context, 0);
        assertEquals("Combat", context.profile);
    }
}
