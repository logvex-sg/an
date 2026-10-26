package com.logvex.crystal.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.combat.PlacementEvaluator.Candidate;
import com.logvex.crystal.combat.PlacementEvaluator.CandidateInput;
import com.logvex.crystal.combat.PlacementEvaluator.Decision;
import com.logvex.crystal.combat.PlacementEvaluator.Plan;
import com.logvex.crystal.combat.PlacementEvaluator.Reason;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the placement validity rules and the rejection reasons they produce. */
class PlacementEvaluatorTest {

    private static CandidateInput candidate(double targetDamage, double selfDamage,
                                            boolean spotFree, boolean baseSolid, boolean inRange) {
        return new CandidateInput(new Candidate(0, 0, 0, targetDamage, selfDamage), spotFree, baseSolid, inRange);
    }

    @Test
    void aGoodCandidateIsAccepted() {
        Decision d = PlacementEvaluator.evaluate(candidate(12, 3, true, true, true),
                true, 6, 20, 8, true);
        assertTrue(d.accepted());
        assertEquals(List.of(), d.reasons());
    }

    @Test
    void outOfRangeIsReported() {
        Decision d = PlacementEvaluator.evaluate(candidate(12, 3, true, true, false),
                true, 6, 20, 8, true);
        assertFalse(d.accepted());
        assertTrue(d.reasons().contains(Reason.OUT_OF_RANGE));
    }

    @Test
    void lowTargetDamageIsReported() {
        Decision d = PlacementEvaluator.evaluate(candidate(2, 3, true, true, true),
                true, 6, 20, 8, true);
        assertTrue(d.reasons().contains(Reason.LOW_TARGET_DAMAGE));
    }

    @Test
    void lethalSelfDamageIsReportedSeparatelyFromHighSelfDamage() {
        Decision lethal = PlacementEvaluator.evaluate(candidate(20, 25, true, true, true),
                true, 6, 20, 8, true);
        assertTrue(lethal.reasons().contains(Reason.LETHAL_SELF_DAMAGE));
        assertFalse(lethal.reasons().contains(Reason.HIGH_SELF_DAMAGE));

        Decision high = PlacementEvaluator.evaluate(candidate(20, 12, true, true, true),
                true, 6, 20, 8, true);
        assertTrue(high.reasons().contains(Reason.HIGH_SELF_DAMAGE));
        assertFalse(high.reasons().contains(Reason.LETHAL_SELF_DAMAGE));
    }

    @Test
    void selfDamageLimitsAreIgnoredWhenAntiSuicideIsOff() {
        Decision d = PlacementEvaluator.evaluate(candidate(20, 40, true, true, true),
                true, 6, 20, 8, false);
        assertTrue(d.accepted());
    }

    @Test
    void everyFailingRuleIsCollected() {
        Decision d = PlacementEvaluator.evaluate(candidate(1, 40, false, false, false),
                true, 6, 20, 8, true);
        assertTrue(d.reasons().containsAll(List.of(
                Reason.OUT_OF_RANGE, Reason.NO_SUPPORT, Reason.SPOT_OCCUPIED,
                Reason.LOW_TARGET_DAMAGE, Reason.LETHAL_SELF_DAMAGE)));
    }

    @Test
    void noTargetIsReported() {
        Decision d = PlacementEvaluator.evaluate(candidate(12, 3, true, true, true),
                false, 6, 20, 8, true);
        assertTrue(d.reasons().contains(Reason.NO_TARGET));
        // With no target, target damage is not a meaningful reason.
        assertFalse(d.reasons().contains(Reason.LOW_TARGET_DAMAGE));
    }

    @Test
    void planPrefersTheHighestTargetMinusSelfScore() {
        CandidateInput low = new CandidateInput(new Candidate(1, 0, 0, 10, 4), true, true, true);
        CandidateInput high = new CandidateInput(new Candidate(2, 0, 0, 14, 2), true, true, true);
        CandidateInput mid = new CandidateInput(new Candidate(3, 0, 0, 12, 3), true, true, true);
        Plan plan = PlacementEvaluator.plan(List.of(low, mid, high), true, 6, 20, 8, true);
        assertTrue(plan.hasPlacement());
        assertEquals(2, plan.best().x());
    }

    @Test
    void planReportsDistinctReasonsWhenNothingIsUsable() {
        CandidateInput far = candidate(12, 1, true, true, false);
        CandidateInput weak = candidate(2, 1, true, true, true);
        Plan plan = PlacementEvaluator.plan(List.of(far, weak), true, 6, 20, 8, true);
        assertFalse(plan.hasPlacement());
        assertTrue(plan.rejected().contains(Reason.OUT_OF_RANGE));
        assertTrue(plan.rejected().contains(Reason.LOW_TARGET_DAMAGE));
    }

    @Test
    void planOnEmptyInputsHasNoPlacement() {
        Plan plan = PlacementEvaluator.plan(List.of(), true, 6, 20, 8, true);
        assertFalse(plan.hasPlacement());
        assertEquals("no candidates", plan.rejectedSummary());
    }

    @Test
    void describeIsHumanReadable() {
        Decision ok = PlacementEvaluator.evaluate(candidate(12, 3, true, true, true), true, 6, 20, 8, true);
        assertTrue(ok.describe().startsWith("ok"));
        Decision bad = PlacementEvaluator.evaluate(candidate(12, 3, true, true, false), true, 6, 20, 8, true);
        assertTrue(bad.describe().contains("out of range"));
    }

    @Test
    void nullInputListIsTolerated() {
        Plan plan = PlacementEvaluator.plan(null, true, 6, 20, 8, true);
        assertFalse(plan.hasPlacement());
    }
}
