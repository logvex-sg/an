package com.logvex.crystal.commandcenter;

/**
 * One source of {@link StatusSection} data for the Command Center.
 *
 * <p>Every subsystem that wants to appear in the Command Center implements this, rather
 * than the Command Center reaching into it directly. That keeps the aggregation open for
 * extension (a new provider appears without touching the service) and testable (a provider
 * is a pure function of already-computed state).
 *
 * <p>A provider must never throw: {@link CommandCenterService} isolates failures, but a
 * provider that throws is reported as an unhealthy section, so it should still try to
 * return its best partial answer.
 */
public interface StatusProvider {

    /** Stable id used to key the section; must be unique within one service. */
    String id();

    /** Display title for the section. */
    String title();

    /** Builds the current section. Called on the client thread; must be cheap. */
    StatusSection snapshot();
}
