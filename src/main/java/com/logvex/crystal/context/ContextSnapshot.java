package com.logvex.crystal.context;

/**
 * An immutable snapshot of the client's situation, evaluated by {@link ContextRule}.
 *
 * <p>Deliberately a plain value so rules can be unit tested without a client. Every field is
 * something the client already knows; nothing here observes the server beyond the data the
 * client is already sent.
 */
public record ContextSnapshot(String dimension, String serverAddress, double healthFraction,
                              boolean playerAlive) {

    public static ContextSnapshot of(String dimension, String serverAddress, double healthFraction,
                                     boolean playerAlive) {
        return new ContextSnapshot(dimension == null ? "" : dimension,
                serverAddress == null ? "" : serverAddress, healthFraction, playerAlive);
    }

    /** A snapshot that matches nothing situational, used when no world is loaded. */
    public static ContextSnapshot none() {
        return new ContextSnapshot("", "", 1.0, false);
    }
}
