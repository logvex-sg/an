package com.logvex.crystal.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the automation rule set to {@code <config>/automation.json}.
 *
 * <p>The schema is versioned. Loading a newer schema than this build understands is refused
 * outright (the rules are left untouched rather than half-read), and a single malformed rule
 * is skipped with a note instead of discarding the whole file. Writes are atomic via
 * {@link ConfigIo}. Minecraft-independent, so the round-trip and migration rules are unit
 * tested directly.
 */
public final class AutomationStore {

    /** Current schema version. Bump when the on-disk shape changes incompatibly. */
    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public AutomationStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** The outcome of a load: the rules that parsed, and how many were skipped. */
    public record LoadResult(List<AutomationRule> rules, boolean enabled, int skipped,
                             boolean refusedNewerSchema) {
    }

    /** Loads the rule set, returning an empty result when the file is absent or unusable. */
    public LoadResult load() {
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return new LoadResult(List.of(), false, 0, false);
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return new LoadResult(List.of(), false, 0, true);
        }
        boolean enabled = root.has("enabled") && root.get("enabled").isJsonPrimitive()
                && root.get("enabled").getAsBoolean();
        List<AutomationRule> rules = new ArrayList<>();
        int skipped = 0;
        if (root.has("rules") && root.get("rules").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("rules")) {
                if (!element.isJsonObject()) {
                    skipped++;
                    continue;
                }
                try {
                    rules.add(readRule(element.getAsJsonObject()));
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
        }
        return new LoadResult(rules, enabled, skipped, false);
    }

    /** Saves the given rules and the global enabled flag atomically. */
    public void save(List<AutomationRule> rules, boolean enabled) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("enabled", enabled);
        JsonArray array = new JsonArray();
        for (AutomationRule rule : rules) {
            array.add(writeRule(rule));
        }
        root.add("rules", array);
        ConfigIo.writeObject(file, root);
    }

    private static AutomationRule readRule(JsonObject object) {
        String id = object.has("id") ? object.get("id").getAsString() : "";
        String name = object.has("name") ? object.get("name").getAsString() : id;
        AutomationRule rule = new AutomationRule(id, name);
        if (object.has("enabled")) {
            rule.enabled(object.get("enabled").getAsBoolean());
        }
        if (object.has("cooldownMillis")) {
            rule.cooldownMillis(object.get("cooldownMillis").getAsLong());
        }
        if (object.has("requiresWorld")) {
            rule.requiresWorld(object.get("requiresWorld").getAsBoolean());
        }
        if (object.has("trigger") && object.get("trigger").isJsonPrimitive()) {
            rule.trigger(Trigger.parse(object.get("trigger").getAsString()));
        }
        if (object.has("conditions") && object.get("conditions").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("conditions")) {
                if (element.isJsonPrimitive()) {
                    rule.addCondition(Condition.parse(element.getAsString()));
                }
            }
        }
        if (object.has("actions") && object.get("actions").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("actions")) {
                if (element.isJsonPrimitive()) {
                    rule.addAction(Action.parse(element.getAsString()));
                }
            }
        }
        return rule;
    }

    private static JsonObject writeRule(AutomationRule rule) {
        JsonObject object = new JsonObject();
        object.addProperty("id", rule.id());
        object.addProperty("name", rule.name());
        object.addProperty("enabled", rule.enabled());
        object.addProperty("cooldownMillis", rule.cooldownMillis());
        object.addProperty("requiresWorld", rule.requiresWorld());
        if (rule.trigger() != null) {
            object.addProperty("trigger", serializeTrigger(rule.trigger()));
        }
        JsonArray conditions = new JsonArray();
        for (Condition condition : rule.conditions()) {
            conditions.add(serializeCondition(condition));
        }
        object.add("conditions", conditions);
        JsonArray actions = new JsonArray();
        for (Action action : rule.actions()) {
            actions.add(serializeAction(action));
        }
        object.add("actions", actions);
        return object;
    }

    // Round-trip helpers: write the same {@code type:arg} syntax the parser accepts, so the
    // file stays human-editable and a hand-written rule loads unchanged.

    private static String serializeTrigger(Trigger trigger) {
        if (trigger instanceof Trigger.Interval interval) {
            return "interval:" + interval.intervalMillis();
        }
        if (trigger instanceof Trigger.Always) {
            return "always";
        }
        if (trigger instanceof Trigger.OnEvent onEvent) {
            return "event:" + onEvent.kind().name();
        }
        if (trigger instanceof Trigger.LowHealth lowHealth) {
            return "lowhealth:" + lowHealth.fraction();
        }
        if (trigger instanceof Trigger.TargetNear near) {
            return "targetnear:" + near.distance();
        }
        if (trigger instanceof Trigger.TargetLost) {
            return "targetlost";
        }
        if (trigger instanceof Trigger.IntegrationAvailable integration) {
            return "integration:" + integration.name();
        }
        if (trigger instanceof Trigger.ModuleEnabled moduleEnabled) {
            return "moduleenabled:" + moduleEnabled.name();
        }
        if (trigger instanceof Trigger.ProfileActive profileActive) {
            return "profileactive:" + profileActive.name();
        }
        return "always";
    }

    private static String serializeCondition(Condition condition) {
        if (condition instanceof Condition.Numeric numeric) {
            return numeric.fact().key() + numeric.comparison().symbol() + numeric.value();
        }
        if (condition instanceof Condition.ModuleEnabled enabledCondition) {
            return "module:" + enabledCondition.name() + "="
                    + (enabledCondition.expected() ? "enabled" : "disabled");
        }
        if (condition instanceof Condition.ProfileIs profileIs) {
            return "profile=" + profileIs.name();
        }
        if (condition instanceof Condition.ActionIdle) {
            return "idle";
        }
        if (condition instanceof Condition.TargetPresent targetPresent) {
            return targetPresent.expected() ? "target" : "notarget";
        }
        return "idle";
    }

    private static String serializeAction(Action action) {
        if (action instanceof Action.EnableModule enable) {
            return "enable:" + enable.name();
        }
        if (action instanceof Action.DisableModule disable) {
            return "disable:" + disable.name();
        }
        if (action instanceof Action.SwitchProfile switchProfile) {
            return "profile:" + switchProfile.name();
        }
        if (action instanceof Action.CancelRoute) {
            return "cancelroute";
        }
        if (action instanceof Action.Notify notify) {
            return "notify:" + notify.level() + ":" + notify.message();
        }
        if (action instanceof Action.Log log) {
            return "log:" + log.message();
        }
        return "log:noop";
    }
}
