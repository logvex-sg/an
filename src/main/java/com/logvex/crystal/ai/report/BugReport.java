package com.logvex.crystal.ai.report;

/**
 * A prepared, already-sanitized bug report. Nothing in here is raw: the builder runs every
 * field through the privacy filter before constructing this, so displaying or sending it can
 * never leak what was filtered out.
 */
public final class BugReport {

    /** Delivery state of a queued report. */
    public enum Status {
        PENDING("Pending"),
        SENT("Sent"),
        FAILED("Failed"),
        DECLINED("Declined");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String id;
    private final String fingerprint;
    private final long createdMillis;
    private final String subject;
    private final String body;
    private final Status status;
    private final int attempts;
    private final String lastError;

    public BugReport(String id, String fingerprint, long createdMillis, String subject, String body,
                     Status status, int attempts, String lastError) {
        this.id = id == null ? "" : id;
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.createdMillis = createdMillis;
        this.subject = subject == null ? "" : subject;
        this.body = body == null ? "" : body;
        this.status = status == null ? Status.PENDING : status;
        this.attempts = attempts;
        this.lastError = lastError == null ? "" : lastError;
    }

    public String id() {
        return id;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public long createdMillis() {
        return createdMillis;
    }

    public String subject() {
        return subject;
    }

    public String body() {
        return body;
    }

    public Status status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    /** Size of the body in bytes (UTF-8), used to enforce the maximum report size. */
    public int sizeBytes() {
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    public BugReport withStatus(Status newStatus, int newAttempts, String error) {
        return new BugReport(id, fingerprint, createdMillis, subject, body, newStatus, newAttempts, error);
    }
}
