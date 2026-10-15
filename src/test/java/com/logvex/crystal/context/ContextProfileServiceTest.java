package com.logvex.crystal.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for the contextual-profile matcher and decision guard rails. */
class ContextProfileServiceTest {

    private static ContextSnapshot snapshot(String dimension, String server, double health) {
        return ContextSnapshot.of(dimension, server, health, true);
    }

    @Test
    void emptyRuleNeverMatches() {
        ContextRule rule = new ContextRule("", "", 0);
        assertFalse(rule.hasConditions());
        assertFalse(rule.matches(snapshot("minecraft:overworld", "play.example.com", 1.0)));
    }

    @Test
    void dimensionRuleMatchesExactly() {
        ContextRule rule = new ContextRule("minecraft:the_nether", "", 0);
        assertTrue(rule.matches(snapshot("minecraft:the_nether", "any", 1.0)));
        assertFalse(rule.matches(snapshot("minecraft:overworld", "any", 1.0)));
    }

    @Test
    void serverRuleMatchesSubstring() {
        ContextRule rule = new ContextRule("", "2b2t.org", 0);
        assertTrue(rule.matches(snapshot("minecraft:overworld", "connect.2b2t.org", 1.0)));
        assertFalse(rule.matches(snapshot("minecraft:overworld", "other.net", 1.0)));
    }

    @Test
    void healthRuleMatchesBelowThreshold() {
        ContextRule rule = new ContextRule("", "", 0.5);
        assertTrue(rule.matches(snapshot("d", "s", 0.3)));
        assertFalse(rule.matches(snapshot("d", "s", 0.8)));
    }

    @Test
    void allConditionsMustHold() {
        ContextRule rule = new ContextRule("minecraft:overworld", "anarchy", 0.5);
        assertTrue(rule.matches(snapshot("minecraft:overworld", "play.anarchy.net", 0.2)));
        assertFalse(rule.matches(snapshot("minecraft:overworld", "play.anarchy.net", 0.9)));
        assertFalse(rule.matches(snapshot("minecraft:the_end", "play.anarchy.net", 0.2)));
    }

    @Test
    void deadPlayerNeverMatches() {
        ContextRule rule = new ContextRule("", "", 0.5);
        assertFalse(rule.matches(new ContextSnapshot("d", "s", 0.1, false)));
    }

    @Test
    void disabledServiceNeverSwitches() {
        ContextProfileService service = new ContextProfileService();
        service.add(ContextProfileBinding.of("nether", new ContextRule("minecraft:the_nether", "", 0)));
        service.setEnabled(false);
        assertTrue(service.decide(snapshot("minecraft:the_nether", "", 1.0), false, 0).isEmpty());
    }

    @Test
    void combatBlocksSwitching() {
        ContextProfileService service = new ContextProfileService();
        service.setEnabled(true);
        service.add(ContextProfileBinding.of("nether", new ContextRule("minecraft:the_nether", "", 0)));
        assertTrue(service.decide(snapshot("minecraft:the_nether", "", 1.0), true, 100000).isEmpty());
    }

    @Test
    void firstMatchingBindingWinsAndIsNotReapplied() {
        ContextProfileService service = new ContextProfileService();
        service.setEnabled(true);
        service.setCooldownMillis(0);
        service.add(ContextProfileBinding.of("first", new ContextRule("minecraft:the_nether", "", 0)));
        service.add(ContextProfileBinding.of("second", new ContextRule("minecraft:the_nether", "", 0)));
        Optional<String> decision = service.decide(snapshot("minecraft:the_nether", "", 1.0), false, 0);
        assertEquals("first", decision.orElse(null));
        service.recordSwitch("first", 0);
        // Already on "first": no further switch.
        assertTrue(service.decide(snapshot("minecraft:the_nether", "", 1.0), false, 1000).isEmpty());
    }

    @Test
    void cooldownBlocksRapidSwitches() {
        ContextProfileService service = new ContextProfileService();
        service.setEnabled(true);
        service.setCooldownMillis(5000);
        service.add(ContextProfileBinding.of("nether", new ContextRule("minecraft:the_nether", "", 0)));
        service.recordSwitch("overworld", 0);
        assertTrue(service.decide(snapshot("minecraft:the_nether", "", 1.0), false, 1000).isEmpty(),
                "still within the cooldown");
        assertEquals("nether", service.decide(snapshot("minecraft:the_nether", "", 1.0), false, 6000)
                .orElse(null));
    }

    @Test
    void noMatchLeavesCurrentProfileAlone() {
        ContextProfileService service = new ContextProfileService();
        service.setEnabled(true);
        service.setCooldownMillis(0);
        service.add(ContextProfileBinding.of("nether", new ContextRule("minecraft:the_nether", "", 0)));
        service.recordSwitch("manual", 0);
        assertTrue(service.decide(snapshot("minecraft:overworld", "", 1.0), false, 100000).isEmpty());
        assertEquals("manual", service.appliedProfile());
    }

    @Test
    void removeDropsEveryBindingForAProfile() {
        ContextProfileService service = new ContextProfileService();
        service.add(ContextProfileBinding.of("nether", new ContextRule("a", "", 0)));
        service.add(ContextProfileBinding.of("nether", new ContextRule("b", "", 0)));
        service.add(ContextProfileBinding.of("other", new ContextRule("c", "", 0)));
        assertEquals(2, service.remove("NETHER"));
        assertEquals(1, service.bindings().size());
    }
}
