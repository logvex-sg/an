package com.logvex.crystal.commandcenter;

/**
 * The outcome of running a {@link CommandCenterAction}: whether it succeeded, a message to
 * show the user, and how healthy the result is.
 *
 * <p>Returning a value rather than throwing keeps the command surface and the GUI simple:
 * both call {@code run()} and render the message, with the health picking the colour.
 */
public record ActionResult(boolean success, String message, HealthStatus status) {

    public ActionResult {
        message = message == null ? "" : message;
        status = status == null ? HealthStatus.OK : status;
    }

    public static ActionResult success(String message) {
        return new ActionResult(true, message, HealthStatus.OK);
    }

    public static ActionResult failure(String message) {
        return new ActionResult(false, message, HealthStatus.ATTENTION);
    }

    public static ActionResult of(boolean success, String message) {
        return new ActionResult(success, message, success ? HealthStatus.OK : HealthStatus.ATTENTION);
    }
}
