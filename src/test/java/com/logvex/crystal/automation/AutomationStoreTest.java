package com.logvex.crystal.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.event.EventKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers trigger/condition/action parsing and the versioned persistence round-trip. */
class AutomationStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void triggerParsingRoundTripsCommonForms() {
        assertEquals(new Trigger.Interval(5000), Trigger.parse("interval:5s"));
        assertEquals(new Trigger.Interval(60_000), Trigger.parse("interval:1m"));
        assertEquals(new Trigger.Always(), Trigger.parse("always"));
        assertEquals(new Trigger.OnEvent(EventKind.TARGET_CHANGED), Trigger.parse("event:TARGET_CHANGED"));
        assertEquals(new Trigger.LowHealth(0.4), Trigger.parse("lowhealth:40"));
        assertEquals(new Trigger.LowHealth(0.4), Trigger.parse("lowhealth:0.4"));
        assertEquals(new Trigger.TargetNear(6.0), Trigger.parse("targetnear:6"));
        assertTrue(Trigger.parse("targetlost") instanceof Trigger.TargetLost);
        assertEquals(new Trigger.IntegrationAvailable("baritone"), Trigger.parse("integration:baritone"));
    }

    @Test
    void unknownTriggerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Trigger.parse("nonsense:1"));
    }

    @Test
    void conditionParsingHandlesOperatorsAndShorthands() {
        assertEquals(new Condition.Numeric(Condition.Fact.HEALTH, Condition.Comparison.LT, 0.5),
                Condition.parse("health<0.5"));
        assertEquals(new Condition.Numeric(Condition.Fact.TARGET_DISTANCE, Condition.Comparison.GTE, 4.0),
                Condition.parse("targetDistance>=4"));
        assertEquals(new Condition.ModuleEnabled("AutoCrystal", true), Condition.parse("module:AutoCrystal=enabled"));
        assertEquals(new Condition.ModuleEnabled("AutoCrystal", false), Condition.parse("module:AutoCrystal=disabled"));
        assertEquals(new Condition.ProfileIs("Combat"), Condition.parse("profile=Combat"));
        assertTrue(Condition.parse("idle") instanceof Condition.ActionIdle);
        assertTrue(Condition.parse("notarget") instanceof Condition.TargetPresent);
    }

    @Test
    void conditionNumericEvaluatesAgainstFacts() {
        Condition.Numeric condition = (Condition.Numeric) Condition.parse("health<=0.5");
        AutomationEngineTestContext context = new AutomationEngineTestContext();
        context.health = 0.4;
        assertTrue(condition.test(context));
        context.health = 0.6;
        assertFalse(condition.test(context));
    }

    @Test
    void actionParsingHandlesNotifyLevels() {
        assertEquals(new Action.Notify("hello", "warn"), Action.parse("notify:warn:hello"));
        assertEquals(new Action.Notify("plain", "info"), Action.parse("notify:plain"));
        assertEquals(new Action.EnableModule("AutoCrystal"), Action.parse("enable:AutoCrystal"));
        assertEquals(new Action.DisableModule("AutoCrystal"), Action.parse("disable:AutoCrystal"));
        assertEquals(new Action.SwitchProfile("Combat"), Action.parse("profile:Combat"));
        assertTrue(Action.parse("cancelroute") instanceof Action.CancelRoute);
    }

    @Test
    void roundTripPreservesRules() throws IOException {
        AutomationStore store = new AutomationStore(tempDir.resolve("automation.json"));
        AutomationRule rule = new AutomationRule("r1", "Low HP escape")
                .trigger(Trigger.parse("lowhealth:30"))
                .cooldownMillis(2500)
                .addCondition(Condition.parse("target"))
                .addAction(Action.parse("enable:Surround"))
                .addAction(Action.parse("notify:warn:low health"));
        store.save(List.of(rule), true);

        AutomationStore.LoadResult result = new AutomationStore(tempDir.resolve("automation.json")).load();
        assertTrue(result.enabled());
        assertEquals(1, result.rules().size());
        AutomationRule loaded = result.rules().get(0);
        assertEquals("Low HP escape", loaded.name());
        assertEquals(2500, loaded.cooldownMillis());
        assertEquals(Trigger.parse("lowhealth:30"), loaded.trigger());
        assertEquals(1, loaded.conditions().size());
        assertEquals(2, loaded.actions().size());
        assertFalse(result.refusedNewerSchema());
    }

    @Test
    void malformedRuleIsSkippedNotFatal() throws IOException {
        Path file = tempDir.resolve("automation.json");
        Files.writeString(file, "{\"schema\":1,\"rules\":[{\"id\":\"a\",\"name\":\"ok\","
                + "\"trigger\":\"always\",\"actions\":[\"log:hi\"]},"
                + "{\"id\":\"b\",\"name\":\"bad\",\"trigger\":\"garbage:1\",\"actions\":[\"log:no\"]}]}");
        AutomationStore.LoadResult result = new AutomationStore(file).load();
        assertEquals(1, result.rules().size());
        assertEquals(1, result.skipped());
    }

    @Test
    void newerSchemaIsRefusedWholesale() throws IOException {
        Path file = tempDir.resolve("automation.json");
        Files.writeString(file, "{\"schema\":999,\"rules\":[{\"id\":\"a\",\"trigger\":\"always\","
                + "\"actions\":[\"log:hi\"]}]}");
        AutomationStore.LoadResult result = new AutomationStore(file).load();
        assertTrue(result.refusedNewerSchema());
        assertTrue(result.rules().isEmpty());
    }

    @Test
    void missingFileYieldsEmptyResult() {
        AutomationStore.LoadResult result = new AutomationStore(tempDir.resolve("absent.json")).load();
        assertTrue(result.rules().isEmpty());
        assertFalse(result.enabled());
        assertFalse(result.refusedNewerSchema());
    }

    /** A minimal context for condition tests. */
    private static final class AutomationEngineTestContext implements AutomationContext {
        double health = 1.0;

        @Override
        public boolean playerAvailable() {
            return true;
        }

        @Override
        public boolean worldAvailable() {
            return true;
        }

        @Override
        public boolean moduleInstalled(String name) {
            return false;
        }

        @Override
        public boolean moduleEnabled(String name) {
            return false;
        }

        @Override
        public boolean integrationAvailable(String name) {
            return false;
        }

        @Override
        public String activeProfile() {
            return "";
        }

        @Override
        public double healthFraction() {
            return health;
        }

        @Override
        public double targetDistance() {
            return -1;
        }

        @Override
        public boolean actionIdle() {
            return true;
        }

        @Override
        public boolean enableModule(String name) {
            return true;
        }

        @Override
        public boolean disableModule(String name) {
            return true;
        }

        @Override
        public boolean switchProfile(String name) {
            return true;
        }

        @Override
        public boolean cancelRoute() {
            return true;
        }

        @Override
        public void notify(String message, String level) {
        }

        @Override
        public void log(String message) {
        }
    }
}
