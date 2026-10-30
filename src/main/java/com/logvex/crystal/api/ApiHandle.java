package com.logvex.crystal.api;

/**
 * A registration handle. Closing it releases the registration exactly once; closing twice is
 * harmless. The {@link ApiRegistry} keeps the handles an owner created so it can release them
 * all on unload even if the owner forgets.
 *
 * <p>Handles are how the API keeps the "every registration has an owner and a cleanup" rule:
 * an extension cannot register something and then have no way to undo it.
 */
public interface ApiHandle extends AutoCloseable {

    /** A short human-readable description of what this handle owns. */
    String describe();

    /** True until {@link #close()} has run. */
    boolean active();

    @Override
    void close();
}
