package com.logvex.crystal.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, Minecraft-free evaluation of end-crystal placement candidates: which spot to use,
 * and — for every candidate that is rejected — exactly why. This is the "placement debugging
 * and rejection reasons" layer: the automation modules and the debug HUD share one definition
 * of an acceptable placement instead of each re-deriving the rules.
 *
 * <p>The evaluator works on plain numbers ({@link Candidate} positions and predicted damage),
 * so the decision logic is unit-testable without a client, and it separates two concerns the
 * mission calls out explicitly:
 *
 * <ul>
 *   <li><b>estimated</b> values (predicted target/self damage, from the {@link CrystalDamage}
 *       model) are inputs here, never presented as confirmed outcomes;</li>
 *   <li><b>validity</b> facts (in range, spot free, support present, target exists) are also
 *       inputs, supplied by the caller that can see the world.</li>
 * </ul>
 *
 * <p>Nothing here touches the world or the network; it only decides.
 */
public final class PlacementEvaluator {

    /** Why a candidate placement was rejected. Order is the order checks are applied. */
    public enum Reason {
        NO_TARGET("no target"),
        OUT_OF_RANGE("out of range"),
        SPOT_OCCUPIED("spot occupied"),
        NO_SUPPORT("no support block"),
        LOW_TARGET_DAMAGE("target damage too low"),
        HIGH_SELF_DAMAGE("self damage too high"),
        LETHAL_SELF_DAMAGE("self damage lethal");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A candidate crystal spot: the crystal block position and the predicted damage. */
    public record Candidate(int x, int y, int z, double targetDamage, double selfDamage) {

        /** Position key for de-duplication. */
        public long key() {
            return (((long) x) << 42) ^ (((long) z) << 21) ^ (y & 0x1FFFFF);
        }
    }

    /** A candidate plus the world facts the evaluator needs to judge it. */
    public record CandidateInput(Candidate candidate, boolean spotFree, boolean baseSolid, boolean inRange) {
    }

    /** The decision for one candidate: whether it is usable, and every reason it is not. */
    public record Decision(Candidate candidate, List<Reason> reasons) {

        public boolean accepted() {
            return reasons.isEmpty();
        }

        /** A short human-readable explanation, e.g. {@code "rejected: out of range, spot occupied"}. */
        public String describe() {
            if (accepted()) {
                return String.format("ok  dmg %.1f/self %.1f", candidate.targetDamage(), candidate.selfDamage());
            }
            StringBuilder sb = new StringBuilder("rejected: ");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(reasons.get(i).label());
            }
            return sb.toString();
        }
    }

    /** The outcome of planning: the best accepted candidate (or null) and the reasons all failed. */
    public record Plan(Candidate best, List<Reason> rejected) {

        public boolean hasPlacement() {
            return best != null;
        }

        /** Distinct rejection reasons across every rejected candidate, in enum order. */
        public String rejectedSummary() {
            if (rejected.isEmpty()) {
                return hasPlacement() ? "accepted" : "no candidates";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rejected.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(rejected.get(i).label());
            }
            return sb.toString();
        }
    }

    private PlacementEvaluator() {
    }

    /**
     * Evaluates a single candidate. Every failing rule is collected (not just the first), so
     * the debug view can show all the reasons a spot is bad at once.
     *
     * @param input          the candidate and its world facts
     * @param hasTarget      whether there is a target to damage at all
     * @param minTargetDamage minimum acceptable predicted damage to the target
     * @param selfHealth     the local player's current health
     * @param selfDamageLimit maximum acceptable self-damage when anti-suicide is on
     * @param antiSuicide    whether self-damage limits apply
     */
    public static Decision evaluate(CandidateInput input, boolean hasTarget, int minTargetDamage,
                                    double selfHealth, int selfDamageLimit, boolean antiSuicide) {
        Candidate c = input.candidate();
        List<Reason> reasons = new ArrayList<>();
        if (!hasTarget) {
            reasons.add(Reason.NO_TARGET);
        }
        if (!input.inRange()) {
            reasons.add(Reason.OUT_OF_RANGE);
        }
        if (!input.baseSolid()) {
            reasons.add(Reason.NO_SUPPORT);
        }
        if (!input.spotFree()) {
            reasons.add(Reason.SPOT_OCCUPIED);
        }
        if (hasTarget && c.targetDamage() < minTargetDamage) {
            reasons.add(Reason.LOW_TARGET_DAMAGE);
        }
        if (antiSuicide) {
            if (c.selfDamage() >= selfHealth) {
                reasons.add(Reason.LETHAL_SELF_DAMAGE);
            } else if (c.selfDamage() > selfDamageLimit) {
                reasons.add(Reason.HIGH_SELF_DAMAGE);
            }
        }
        return new Decision(c, List.copyOf(reasons));
    }

    /**
     * Picks the best accepted candidate from a list, preferring the one with the highest
     * "target damage minus self damage" score. Returns every distinct rejection reason when
     * none is usable, so a module can explain why it did nothing.
     */
    public static Plan plan(List<CandidateInput> inputs, boolean hasTarget, int minTargetDamage,
                            double selfHealth, int selfDamageLimit, boolean antiSuicide) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        List<Reason> rejected = new ArrayList<>();
        if (inputs != null) {
            for (CandidateInput input : inputs) {
                Decision decision = evaluate(input, hasTarget, minTargetDamage, selfHealth,
                        selfDamageLimit, antiSuicide);
                if (decision.accepted()) {
                    double score = input.candidate().targetDamage() - input.candidate().selfDamage();
                    if (score > bestScore) {
                        bestScore = score;
                        best = input.candidate();
                    }
                } else {
                    for (Reason reason : decision.reasons()) {
                        if (!rejected.contains(reason)) {
                            rejected.add(reason);
                        }
                    }
                }
            }
        }
        return new Plan(best, List.copyOf(rejected));
    }
}
