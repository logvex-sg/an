package com.logvex.crystal.automation;

/**
 * What causes a rule to be considered: a timer, a recurring tick, a client event, or a fact
 * about the current state. A rule with no trigger never fires.
 */
public sealed interface Trigger {

    /** A short human-readable description for the editor and the command listing. */
    String describe();

    /** Elapsed-time trigger: fires when at least {@code intervalMillis} has passed since last fire. */
    record Interval(long intervalMillis) implements Trigger {
        @Override
        public String describe() {
            return "every " + intervalMillis + "ms";
        }
    }

    /** Fires on every evaluation tick while the rule's conditions hold. */
    record Always() implements Trigger {
        @Override
        public String describe() {
            return "every tick";
        }
    }

    /** Fires when an event of the given kind is observed. */
    record OnEvent(com.logvex.crystal.event.EventKind kind) implements Trigger {
        @Override
        public String describe() {
            return "on " + kind;
        }
    }

    /** Fires when the player's health fraction is at or below {@code fraction}. */
    record LowHealth(double fraction) implements Trigger {
        @Override
        public String describe() {
            return "health <= " + Math.round(fraction * 100) + "%";
        }
    }

    /** Fires when a combat target is within {@code distance} blocks. */
    record TargetNear(double distance) implements Trigger {
        @Override
        public String describe() {
            return "target within " + distance;
        }
    }

    /** Fires when no combat target is present. */
    record TargetLost() implements Trigger {
        @Override
        public String describe() {
            return "no target";
        }
    }

    /** Fires when an optional integration is available. */
    record IntegrationAvailable(String name) implements Trigger {
        @Override
        public String describe() {
            return "integration " + name + " available";
        }
    }

    /** Fires when a named module is enabled. */
    record ModuleEnabled(String name) implements Trigger {
        @Override
        public String describe() {
            return "module " + name + " enabled";
        }
    }

    /** Fires when the active profile equals {@code name}. */
    record ProfileActive(String name) implements Trigger {
        @Override
        public String describe() {
            return "profile " + name;
        }
    }

    /** Parses a trigger from {@code type:arg} syntax. Throws on an unknown type. */
    static Trigger parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("empty trigger");
        }
        int colon = spec.indexOf(':');
        String type = (colon < 0 ? spec : spec.substring(0, colon)).trim().toLowerCase(java.util.Locale.ROOT);
        String arg = colon < 0 ? "" : spec.substring(colon + 1).trim();
        return switch (type) {
            case "interval" -> new Interval(parseMillis(arg));
            case "always", "tick" -> new Always();
            case "event" -> new OnEvent(com.logvex.crystal.event.EventKind.valueOf(
                    arg.toUpperCase(java.util.Locale.ROOT)));
            case "lowhealth", "health" -> new LowHealth(parseFraction(arg));
            case "targetnear" -> new TargetNear(Double.parseDouble(arg));
            case "targetlost", "notarget" -> new TargetLost();
            case "integration" -> new IntegrationAvailable(arg);
            case "moduleenabled" -> new ModuleEnabled(arg);
            case "profileactive" -> new ProfileActive(arg);
            default -> throw new IllegalArgumentException("unknown trigger type '" + type + "'");
        };
    }

    private static long parseMillis(String arg) {
        if (arg.isEmpty()) {
            throw new IllegalArgumentException("interval needs a duration");
        }
        String value = arg.toLowerCase(java.util.Locale.ROOT);
        long multiplier = 1;
        if (value.endsWith("s")) {
            multiplier = 1000;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 60_000;
            value = value.substring(0, value.length() - 1);
        }
        return Math.max(1, (long) (Double.parseDouble(value) * multiplier));
    }

    private static double parseFraction(String arg) {
        double value = Double.parseDouble(arg);
        if (value > 1.0) {
            value /= 100.0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
