package com.logvex.crystal.ai;

/**
 * All Phase 7 preferences. Plain mutable fields (like {@code ThemeSettings}) so the codec
 * stays trivial and Minecraft-independent; {@link #clamp()} bounds every value.
 *
 * <p>Defaults follow the spec: crash detection and the report queue are on, local AI analysis
 * is opt-in, email sending is off until the user explicitly enables it, and each report needs
 * individual approval. Nothing here starts the model or sends mail by itself.
 */
public final class AISettings {

    /** Runtime resource limits (also the queue bounds the executor enforces). */
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 2;
    public static final int MIN_QUEUE = 1;
    public static final int MAX_QUEUE = 3;

    // --- enablement ---
    public boolean enabled = true;
    public boolean crashDetection = true;
    public boolean localAnalysis = false;
    public boolean reportQueue = true;
    public boolean emailEnabled = false;
    public boolean askBeforeSending = true;
    public boolean includeEnvironment = true;
    public boolean includeLogs = true;
    public boolean keepLocalHistory = true;
    public boolean catCosmetics = true;

    // --- privacy / limits ---
    /** Extra explicit names to redact (one per line). */
    public String extraRedactions = "";
    public int maxReportKilobytes = 64;
    public int maxHistory = 40;

    // --- model ---
    public String modelId = "qwen2.5-0.5b-instruct-q4_k_m";
    public int idleUnloadMinutes = 10;
    public int inferenceThreads = 1;
    public int pendingQueueLimit = 2;
    public int inferenceTimeoutSeconds = 20;

    // --- SMTP (user supplied; never bundled) ---
    public String smtpHost = "";
    public int smtpPort = 587;
    public String smtpUser = "";
    public String smtpPassword = "";
    public boolean smtpStartTls = true;
    public String smtpFrom = "";

    /** Clamps every value into a safe range. Returns this for chaining. */
    public AISettings clamp() {
        maxReportKilobytes = Math.max(8, Math.min(512, maxReportKilobytes));
        maxHistory = Math.max(5, Math.min(200, maxHistory));
        idleUnloadMinutes = Math.max(1, Math.min(240, idleUnloadMinutes));
        inferenceThreads = Math.max(MIN_THREADS, Math.min(MAX_THREADS, inferenceThreads));
        pendingQueueLimit = Math.max(MIN_QUEUE, Math.min(MAX_QUEUE, pendingQueueLimit));
        inferenceTimeoutSeconds = Math.max(3, Math.min(120, inferenceTimeoutSeconds));
        smtpPort = Math.max(1, Math.min(65535, smtpPort));
        if (extraRedactions == null) {
            extraRedactions = "";
        }
        return this;
    }

    /** The queue bound actually handed to the executor (0 disables AI work). */
    public int effectiveQueueLimit() {
        return enabled ? Math.max(MIN_QUEUE, Math.min(MAX_QUEUE, pendingQueueLimit)) : 0;
    }
}
