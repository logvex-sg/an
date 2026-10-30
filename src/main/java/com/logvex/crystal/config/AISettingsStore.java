package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.ai.AISettings;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists {@link AISettings} to {@code <config>/ai.json} using the shared atomic write, so a
 * crash during a save cannot corrupt the file. The schema is versioned; a newer file is refused
 * rather than half-read.
 */
public final class AISettingsStore {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;

    public AISettingsStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Reads the stored settings into {@code settings}; leaves defaults when absent/unusable. */
    public void loadInto(AISettings settings) {
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        settings.enabled = bool(root, "enabled", settings.enabled);
        settings.crashDetection = bool(root, "crashDetection", settings.crashDetection);
        settings.localAnalysis = bool(root, "localAnalysis", settings.localAnalysis);
        settings.reportQueue = bool(root, "reportQueue", settings.reportQueue);
        settings.emailEnabled = bool(root, "emailEnabled", settings.emailEnabled);
        settings.askBeforeSending = bool(root, "askBeforeSending", settings.askBeforeSending);
        settings.includeEnvironment = bool(root, "includeEnvironment", settings.includeEnvironment);
        settings.includeLogs = bool(root, "includeLogs", settings.includeLogs);
        settings.keepLocalHistory = bool(root, "keepLocalHistory", settings.keepLocalHistory);
        settings.catCosmetics = bool(root, "catCosmetics", settings.catCosmetics);
        settings.extraRedactions = string(root, "extraRedactions", settings.extraRedactions);
        settings.maxReportKilobytes = integer(root, "maxReportKilobytes", settings.maxReportKilobytes);
        settings.maxHistory = integer(root, "maxHistory", settings.maxHistory);
        settings.modelId = string(root, "modelId", settings.modelId);
        settings.idleUnloadMinutes = integer(root, "idleUnloadMinutes", settings.idleUnloadMinutes);
        settings.inferenceThreads = integer(root, "inferenceThreads", settings.inferenceThreads);
        settings.pendingQueueLimit = integer(root, "pendingQueueLimit", settings.pendingQueueLimit);
        settings.inferenceTimeoutSeconds = integer(root, "inferenceTimeoutSeconds", settings.inferenceTimeoutSeconds);
        settings.smtpHost = string(root, "smtpHost", settings.smtpHost);
        settings.smtpPort = integer(root, "smtpPort", settings.smtpPort);
        settings.smtpUser = string(root, "smtpUser", settings.smtpUser);
        settings.smtpPassword = string(root, "smtpPassword", settings.smtpPassword);
        settings.smtpStartTls = bool(root, "smtpStartTls", settings.smtpStartTls);
        settings.smtpFrom = string(root, "smtpFrom", settings.smtpFrom);
        settings.clamp();
    }

    /** Writes the settings atomically. */
    public void save(AISettings settings) throws IOException {
        settings.clamp();
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("enabled", settings.enabled);
        root.addProperty("crashDetection", settings.crashDetection);
        root.addProperty("localAnalysis", settings.localAnalysis);
        root.addProperty("reportQueue", settings.reportQueue);
        root.addProperty("emailEnabled", settings.emailEnabled);
        root.addProperty("askBeforeSending", settings.askBeforeSending);
        root.addProperty("includeEnvironment", settings.includeEnvironment);
        root.addProperty("includeLogs", settings.includeLogs);
        root.addProperty("keepLocalHistory", settings.keepLocalHistory);
        root.addProperty("catCosmetics", settings.catCosmetics);
        root.addProperty("extraRedactions", settings.extraRedactions);
        root.addProperty("maxReportKilobytes", settings.maxReportKilobytes);
        root.addProperty("maxHistory", settings.maxHistory);
        root.addProperty("modelId", settings.modelId);
        root.addProperty("idleUnloadMinutes", settings.idleUnloadMinutes);
        root.addProperty("inferenceThreads", settings.inferenceThreads);
        root.addProperty("pendingQueueLimit", settings.pendingQueueLimit);
        root.addProperty("inferenceTimeoutSeconds", settings.inferenceTimeoutSeconds);
        root.addProperty("smtpHost", settings.smtpHost);
        root.addProperty("smtpPort", settings.smtpPort);
        root.addProperty("smtpUser", settings.smtpUser);
        root.addProperty("smtpPassword", settings.smtpPassword);
        root.addProperty("smtpStartTls", settings.smtpStartTls);
        root.addProperty("smtpFrom", settings.smtpFrom);
        ConfigIo.writeObject(file, root);
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int integer(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
