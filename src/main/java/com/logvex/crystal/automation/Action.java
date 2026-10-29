package com.logvex.crystal.automation;

/**
 * Something a rule does when it fires. Actions are verified: each returns whether it took
 * effect, so the engine can record success or failure rather than assuming.
 */
public sealed interface Action {

    /** A short description for the editor and command listing. */
    String describe();

    /**
     * Performs the action.
     *
     * @return true when the action took effect
     */
    boolean execute(AutomationContext context);

    /** Enables a module. */
    record EnableModule(String name) implements Action {
        @Override
        public String describe() {
            return "enable " + name;
        }

        @Override
        public boolean execute(AutomationContext context) {
            return context.enableModule(name);
        }
    }

    /** Disables a module. */
    record DisableModule(String name) implements Action {
        @Override
        public String describe() {
            return "disable " + name;
        }

        @Override
        public boolean execute(AutomationContext context) {
            return context.disableModule(name);
        }
    }

    /** Switches to a named profile. */
    record SwitchProfile(String name) implements Action {
        @Override
        public String describe() {
            return "profile " + name;
        }

        @Override
        public boolean execute(AutomationContext context) {
            return context.switchProfile(name);
        }
    }

    /** Cancels the current Baritone route. */
    record CancelRoute() implements Action {
        @Override
        public String describe() {
            return "cancel route";
        }

        @Override
        public boolean execute(AutomationContext context) {
            return context.cancelRoute();
        }
    }

    /** Posts a notification. */
    record Notify(String message, String level) implements Action {
        @Override
        public String describe() {
            return "notify " + level + " \"" + message + "\"";
        }

        @Override
        public boolean execute(AutomationContext context) {
            context.notify(message, level);
            return true;
        }
    }

    /** Writes a diagnostics line. */
    record Log(String message) implements Action {
        @Override
        public String describe() {
            return "log \"" + message + "\"";
        }

        @Override
        public boolean execute(AutomationContext context) {
            context.log(message);
            return true;
        }
    }

    /**
     * Parses an action from one of:
     * {@code enable:Name}, {@code disable:Name}, {@code profile:Name}, {@code cancelroute},
     * {@code notify:level:message}, {@code log:message}.
     */
    static Action parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("empty action");
        }
        String token = spec.trim();
        int colon = token.indexOf(':');
        String type = (colon < 0 ? token : token.substring(0, colon)).trim().toLowerCase(java.util.Locale.ROOT);
        String arg = colon < 0 ? "" : token.substring(colon + 1).trim();
        return switch (type) {
            case "enable" -> new EnableModule(require(arg, "enable needs a module name"));
            case "disable" -> new DisableModule(require(arg, "disable needs a module name"));
            case "profile" -> new SwitchProfile(require(arg, "profile needs a name"));
            case "cancelroute", "cancel" -> new CancelRoute();
            case "notify" -> parseNotify(arg);
            case "log" -> new Log(require(arg, "log needs a message"));
            default -> throw new IllegalArgumentException("unknown action type '" + type + "'");
        };
    }

    private static Action parseNotify(String arg) {
        String level = "info";
        String message = arg;
        int second = arg.indexOf(':');
        if (second > 0) {
            String candidate = arg.substring(0, second).trim().toLowerCase(java.util.Locale.ROOT);
            if (candidate.equals("info") || candidate.equals("success") || candidate.equals("warn")
                    || candidate.equals("error")) {
                level = candidate;
                message = arg.substring(second + 1).trim();
            }
        }
        return new Notify(require(message, "notify needs a message"), level);
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
