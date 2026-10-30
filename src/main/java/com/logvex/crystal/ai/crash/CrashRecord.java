package com.logvex.crystal.ai.crash;

import java.util.ArrayList;
import java.util.List;

/**
 * A single captured failure. This is a plain, Minecraft-independent value object so it can
 * be built on the crash thread (fast, allocation-light), written to disk, reloaded on the
 * next launch, and unit tested without a game.
 *
 * <p>Everything here is already privacy-relevant: the raw stack trace is kept, but a report
 * only ever exposes the {@link com.logvex.crystal.ai.privacy.PrivacyFilter filtered} form.
 */
public final class CrashRecord {

    /** Bumped when the persisted shape changes; a newer record is refused, not guessed at. */
    public static final int SCHEMA_VERSION = 1;

    private final String id;
    private final String fingerprint;
    private final long timestampMillis;
    private final String exceptionType;
    private final String exceptionMessage;
    private final String rawStackTrace;
    private final CrashCategory category;
    private final CrashSeverity severity;
    private final String suspectedModule;
    private final String source;

    // Environment snapshot, all strings so the record serialises without a Minecraft dep.
    private final String modVersion;
    private final String minecraftVersion;
    private final String loaderVersion;
    private final String javaVersion;
    private final String osName;
    private final String baritoneStatus;
    private final String aiStatus;
    private final List<String> recentEvents;

    private CrashRecord(Builder builder) {
        this.id = builder.id;
        this.fingerprint = builder.fingerprint;
        this.timestampMillis = builder.timestampMillis;
        this.exceptionType = builder.exceptionType;
        this.exceptionMessage = builder.exceptionMessage;
        this.rawStackTrace = builder.rawStackTrace;
        this.category = builder.category;
        this.severity = builder.severity;
        this.suspectedModule = builder.suspectedModule;
        this.source = builder.source;
        this.modVersion = builder.modVersion;
        this.minecraftVersion = builder.minecraftVersion;
        this.loaderVersion = builder.loaderVersion;
        this.javaVersion = builder.javaVersion;
        this.osName = builder.osName;
        this.baritoneStatus = builder.baritoneStatus;
        this.aiStatus = builder.aiStatus;
        this.recentEvents = List.copyOf(builder.recentEvents);
    }

    public String id() {
        return id;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public String exceptionType() {
        return exceptionType;
    }

    public String exceptionMessage() {
        return exceptionMessage;
    }

    public String rawStackTrace() {
        return rawStackTrace;
    }

    public CrashCategory category() {
        return category;
    }

    public CrashSeverity severity() {
        return severity;
    }

    public String suspectedModule() {
        return suspectedModule;
    }

    public String source() {
        return source;
    }

    public String modVersion() {
        return modVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String loaderVersion() {
        return loaderVersion;
    }

    public String javaVersion() {
        return javaVersion;
    }

    public String osName() {
        return osName;
    }

    public String baritoneStatus() {
        return baritoneStatus;
    }

    public String aiStatus() {
        return aiStatus;
    }

    public List<String> recentEvents() {
        return recentEvents;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; only {@link CrashMonitor} and the store populate the environment fields. */
    public static final class Builder {
        private String id = "";
        private String fingerprint = "";
        private long timestampMillis;
        private String exceptionType = "";
        private String exceptionMessage = "";
        private String rawStackTrace = "";
        private CrashCategory category = CrashCategory.UNKNOWN;
        private CrashSeverity severity = CrashSeverity.MEDIUM;
        private String suspectedModule = "";
        private String source = "unknown";
        private String modVersion = "unknown";
        private String minecraftVersion = "unknown";
        private String loaderVersion = "unknown";
        private String javaVersion = "unknown";
        private String osName = "unknown";
        private String baritoneStatus = "unknown";
        private String aiStatus = "unknown";
        private final List<String> recentEvents = new ArrayList<>();

        public Builder id(String value) {
            this.id = value == null ? "" : value;
            return this;
        }

        public Builder fingerprint(String value) {
            this.fingerprint = value == null ? "" : value;
            return this;
        }

        public Builder timestampMillis(long value) {
            this.timestampMillis = value;
            return this;
        }

        public Builder exceptionType(String value) {
            this.exceptionType = value == null ? "" : value;
            return this;
        }

        public Builder exceptionMessage(String value) {
            this.exceptionMessage = value == null ? "" : value;
            return this;
        }

        public Builder rawStackTrace(String value) {
            this.rawStackTrace = value == null ? "" : value;
            return this;
        }

        public Builder category(CrashCategory value) {
            this.category = value == null ? CrashCategory.UNKNOWN : value;
            return this;
        }

        public Builder severity(CrashSeverity value) {
            this.severity = value == null ? CrashSeverity.MEDIUM : value;
            return this;
        }

        public Builder suspectedModule(String value) {
            this.suspectedModule = value == null ? "" : value;
            return this;
        }

        public Builder source(String value) {
            this.source = value == null ? "unknown" : value;
            return this;
        }

        public Builder modVersion(String value) {
            this.modVersion = value == null ? "unknown" : value;
            return this;
        }

        public Builder minecraftVersion(String value) {
            this.minecraftVersion = value == null ? "unknown" : value;
            return this;
        }

        public Builder loaderVersion(String value) {
            this.loaderVersion = value == null ? "unknown" : value;
            return this;
        }

        public Builder javaVersion(String value) {
            this.javaVersion = value == null ? "unknown" : value;
            return this;
        }

        public Builder osName(String value) {
            this.osName = value == null ? "unknown" : value;
            return this;
        }

        public Builder baritoneStatus(String value) {
            this.baritoneStatus = value == null ? "unknown" : value;
            return this;
        }

        public Builder aiStatus(String value) {
            this.aiStatus = value == null ? "unknown" : value;
            return this;
        }

        public Builder recentEvents(List<String> value) {
            this.recentEvents.clear();
            if (value != null) {
                this.recentEvents.addAll(value);
            }
            return this;
        }

        public Builder addEvent(String value) {
            if (value != null && !value.isBlank()) {
                this.recentEvents.add(value);
            }
            return this;
        }

        public CrashRecord build() {
            return new CrashRecord(this);
        }
    }
}
