package com.logvex.crystal.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Round-trip and robustness tests for the contextual-profile codec. */
class ContextStoreTest {

    @Test
    void roundTripsRules() {
        ContextProfileService service = new ContextProfileService();
        service.setEnabled(true);
        service.setCooldownMillis(1234);
        service.add(ContextProfileBinding.of("nether",
                new ContextRule("minecraft:the_nether", "anarchy", 0.5)));

        ContextProfileService restored = new ContextProfileService();
        ContextStore.apply(ContextStore.toJson(service), restored);

        assertTrue(restored.enabled());
        assertEquals(1234, restored.cooldownMillis());
        assertEquals(1, restored.bindings().size());
        ContextRule rule = restored.bindings().get(0).rule();
        assertEquals("minecraft:the_nether", rule.dimension());
        assertEquals("anarchy", rule.serverContains());
        assertEquals(0.5, rule.healthBelow(), 1e-6);
    }

    @Test
    void newerSchemaIsIgnored() {
        ContextProfileService service = new ContextProfileService();
        JsonObject root = ContextStore.toJson(service);
        root.addProperty("schema", ContextStore.SCHEMA_VERSION + 1);
        root.addProperty("enabled", true);
        ContextProfileService target = new ContextProfileService();
        ContextStore.apply(root, target);
        assertFalse(target.enabled());
    }

    @Test
    void malformedRuleIsSkipped() {
        ContextProfileService service = new ContextProfileService();
        JsonObject root = JsonParser.parseString("{\"schema\":1,\"rules\":["
                + "{\"profile\":\"\"},"
                + "{\"profile\":\"ok\",\"dimension\":\"d\"},"
                + "\"not-an-object\"]}").getAsJsonObject();
        ContextStore.apply(root, service);
        assertEquals(1, service.bindings().size());
        assertEquals("ok", service.bindings().get(0).profileName());
    }

    @Test
    void applyReplacesExistingRules() {
        ContextProfileService service = new ContextProfileService();
        service.add(ContextProfileBinding.of("stale", new ContextRule("x", "", 0)));
        ContextStore.apply(JsonParser.parseString("{\"schema\":1,\"rules\":[]}").getAsJsonObject(), service);
        assertTrue(service.bindings().isEmpty());
    }
}
