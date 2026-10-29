package com.logvex.crystal.automation;

/**
 * A guard a rule must satisfy before its actions run. Conditions are pure predicates over
 * {@link AutomationContext} facts; they never mutate anything.
 */
public sealed interface Condition {

    /** A short description for the editor and command listing. */
    String describe();

    /** Whether this condition holds right now. */
    boolean test(AutomationContext context);

    /** Numeric comparison operators. */
    enum Comparison {
        LT("<"), LTE("<="), GT(">"), GTE(">="), EQ("=="), NE("!=");

        private final String symbol;

        Comparison(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public boolean test(double left, double right) {
            return switch (this) {
                case LT -> left < right;
                case LTE -> left <= right;
                case GT -> left > right;
                case GTE -> left >= right;
                case EQ -> left == right;
                case NE -> left != right;
            };
        }

        static Comparison parse(String token) {
            return switch (token) {
                case "<" -> LT;
                case "<=" -> LTE;
                case ">" -> GT;
                case ">=" -> GTE;
                case "==", "=" -> EQ;
                case "!=", "!" -> NE;
                default -> throw new IllegalArgumentException("unknown comparison '" + token + "'");
            };
        }
    }

    /** A named numeric fact. */
    enum Fact {
        HEALTH("health"), TARGET_DISTANCE("targetDistance");

        private final String key;

        Fact(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Fact parse(String token) {
            String normalised = token.toLowerCase(java.util.Locale.ROOT);
            return switch (normalised) {
                case "health" -> HEALTH;
                case "targetdistance", "distance", "target" -> TARGET_DISTANCE;
                default -> throw new IllegalArgumentException("unknown fact '" + token + "'");
            };
        }
    }

    /** Compares a numeric fact against a constant. */
    record Numeric(Fact fact, Comparison comparison, double value) implements Condition {
        @Override
        public String describe() {
            return fact.key() + " " + comparison.symbol() + " " + value;
        }

        @Override
        public boolean test(AutomationContext context) {
            double current = switch (fact) {
                case HEALTH -> context.healthFraction();
                case TARGET_DISTANCE -> context.targetDistance();
            };
            return comparison.test(current, value);
        }
    }

    /** Requires a module to be enabled (or disabled). */
    record ModuleEnabled(String name, boolean expected) implements Condition {
        @Override
        public String describe() {
            return "module " + name + (expected ? " enabled" : " disabled");
        }

        @Override
        public boolean test(AutomationContext context) {
            return context.moduleEnabled(name) == expected;
        }
    }

    /** Requires the active profile to be a specific name. */
    record ProfileIs(String name) implements Condition {
        @Override
        public String describe() {
            return "profile == " + name;
        }

        @Override
        public boolean test(AutomationContext context) {
            return name.equalsIgnoreCase(context.activeProfile());
        }
    }

    /** Requires no client action to be running. */
    record ActionIdle() implements Condition {
        @Override
        public String describe() {
            return "action idle";
        }

        @Override
        public boolean test(AutomationContext context) {
            return context.actionIdle();
        }
    }

    /** Requires a target to be present (or absent). */
    record TargetPresent(boolean expected) implements Condition {
        @Override
        public String describe() {
            return expected ? "target present" : "no target";
        }

        @Override
        public boolean test(AutomationContext context) {
            return (context.targetDistance() >= 0) == expected;
        }
    }

    /**
     * Parses a condition from one of:
     * {@code health<0.5}, {@code targetDistance>=4}, {@code module:Name=enabled},
     * {@code profile=Name}, {@code idle}, {@code target}, {@code notarget}.
     */
    static Condition parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("empty condition");
        }
        String token = spec.trim();
        if (token.equalsIgnoreCase("idle")) {
            return new ActionIdle();
        }
        if (token.equalsIgnoreCase("target")) {
            return new TargetPresent(true);
        }
        if (token.equalsIgnoreCase("notarget") || token.equalsIgnoreCase("no-target")) {
            return new TargetPresent(false);
        }
        if (token.toLowerCase(java.util.Locale.ROOT).startsWith("module:")) {
            String rest = token.substring("module:".length());
            int equals = rest.indexOf('=');
            if (equals < 0) {
                return new ModuleEnabled(rest.trim(), true);
            }
            String name = rest.substring(0, equals).trim();
            String state = rest.substring(equals + 1).trim();
            return new ModuleEnabled(name, !state.equalsIgnoreCase("disabled") && !state.equals("false"));
        }
        if (token.toLowerCase(java.util.Locale.ROOT).startsWith("profile=")) {
            return new ProfileIs(token.substring("profile=".length()).trim());
        }
        for (String operator : new String[]{">=", "<=", "!=", "==", ">", "<", "="}) {
            int index = token.indexOf(operator);
            if (index > 0) {
                String fact = token.substring(0, index).trim();
                String value = token.substring(index + operator.length()).trim();
                return new Numeric(Fact.parse(fact), Comparison.parse(operator), Double.parseDouble(value));
            }
        }
        throw new IllegalArgumentException("unrecognised condition '" + spec + "'");
    }
}
