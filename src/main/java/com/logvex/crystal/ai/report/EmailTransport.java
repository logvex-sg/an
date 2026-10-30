package com.logvex.crystal.ai.report;

import java.io.IOException;

/** Delivers a prepared report. Implementations are blocking and run off the game thread. */
public interface EmailTransport {

    /** Sends one message. Throws when delivery fails; the caller records the error. */
    void send(String recipient, String subject, String body) throws IOException;
}
