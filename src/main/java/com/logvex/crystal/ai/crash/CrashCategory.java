package com.logvex.crystal.ai.crash;

/**
 * The coarse bucket a captured failure falls into. Classification is deterministic (see
 * {@link CrashClassifier}) so it works with no AI model present; the local model can only
 * add explanation on top, never change the bucket.
 */
public enum CrashCategory {

    OUT_OF_MEMORY("Out of memory", CrashSeverity.CRITICAL),
    RENDER_DRIVER("Render / driver", CrashSeverity.HIGH),
    MOD_INCOMPATIBILITY("Mod incompatibility", CrashSeverity.HIGH),
    FABRIC_LOADER("Fabric loader", CrashSeverity.HIGH),
    MODULE_EXCEPTION("Client module", CrashSeverity.MEDIUM),
    BARITONE("Baritone integration", CrashSeverity.MEDIUM),
    CONFIG("Configuration", CrashSeverity.LOW),
    WORLD_STATE("World / state", CrashSeverity.MEDIUM),
    UNKNOWN("Unclassified", CrashSeverity.MEDIUM);

    private final String label;
    private final CrashSeverity defaultSeverity;

    CrashCategory(String label, CrashSeverity defaultSeverity) {
        this.label = label;
        this.defaultSeverity = defaultSeverity;
    }

    public String label() {
        return label;
    }

    public CrashSeverity defaultSeverity() {
        return defaultSeverity;
    }
}
