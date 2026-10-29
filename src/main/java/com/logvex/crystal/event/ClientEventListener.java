package com.logvex.crystal.event;

/**
 * A subscriber to the {@link ClientEventBus}.
 *
 * <p>Callbacks run on the client thread, synchronously, while the bus dispatches. A listener
 * must therefore be fast and must never throw: the bus isolates failures, but a slow listener
 * would stall the tick. Listeners that need to do real work should record a flag and act on
 * their own tick.
 */
@FunctionalInterface
public interface ClientEventListener {

    /** Called for each event the subscription matches. Must not throw or block. */
    void onEvent(ClientEvent event);
}
