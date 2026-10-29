package com.logvex.crystal.extension;

import com.logvex.crystal.api.Capabilities;

/**
 * The runtime state of one extension as the management UI and command surface see it.
 *
 * <p>An extension is only ever reported {@code ACTIVE} after {@code initialize} returned
 * without throwing. Every other outcome has its own state so a failure is visible rather than
 * hidden behind a generic "disabled".
 */
public final class ExtensionState {

    public enum Status {
        /** Discovered, manifest valid and compatible, not yet enabled by the user. */
        AVAILABLE,
        /** Initialized successfully and currently active. */
        ACTIVE,
        /** The user disabled it (or it was never enabled). */
        DISABLED,
        /** The manifest was rejected before any code ran. */
        INVALID,
        /** The manifest is valid but requires an API this client does not provide. */
        INCOMPATIBLE,
        /** Initialization threw; registrations were rolled back. */
        FAILED
    }

    private final ExtensionManifest manifest;
    private final String modId;
    private Status status;
    private String detail;

    public ExtensionState(ExtensionManifest manifest, String modId, Status status, String detail) {
        this.manifest = manifest;
        this.modId = modId;
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    public ExtensionManifest manifest() {
        return manifest;
    }

    public String id() {
        return manifest == null ? modId : manifest.id();
    }

    public String modId() {
        return modId;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    void set(Status status, String detail) {
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    /** True only when the extension initialized successfully and is running. */
    public boolean loaded() {
        return status == Status.ACTIVE;
    }

    /** True when the user could enable this extension now. */
    public boolean enableable() {
        return status == Status.AVAILABLE || status == Status.DISABLED;
    }

    /** A one-line summary for the command surface. */
    public String summary() {
        String base = id() + " [" + status + "] " + (manifest == null ? modId : manifest.summary());
        return detail.isEmpty() ? base : base + " - " + detail;
    }

    /** A short capability line for the management UI. */
    public String capabilityLine(Capabilities capabilities) {
        if (manifest == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String capability : manifest.capabilities()) {
            builder.append(capabilities.has(capability) ? "\u2713 " : "\u2717 ").append(capability).append(' ');
        }
        return builder.toString().trim();
    }
}
