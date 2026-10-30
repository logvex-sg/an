package com.logvex.crystal.ai.crash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic crash analysis. This runs first and on its own: it needs no model, so crash
 * explanations are available even when AI is disabled or the runtime is missing. Every claim
 * is either a {@code fact} (read straight from the record) or a {@code hypothesis} (a
 * possibility suggested by a matched rule); nothing here asserts a cause it cannot support.
 *
 * <p>Pattern rules cover the common Fabric/Minecraft/Java failures by name. When a local model
 * is available the {@link com.logvex.crystal.ai.MiniAIManager} may add an extra explanation, but
 * it can never overwrite the deterministic verdict.
 */
public final class CrashAnalyzer {

    /** A known-error rule: a substring to look for and the guidance to give. */
    private record Rule(String needle, String cause, String fix) {
    }

    private static final List<Rule> KNOWN = List.of(
            new Rule("java.lang.outofmemoryerror",
                    "The JVM ran out of heap while the game was running.",
                    "Raise the allocated RAM in the launcher (-Xmx), lower render distance, and disable heavy ESP modules."),
            new Rule("glfw error 65542",
                    "The OpenGL driver could not be initialised.",
                    "Update your graphics driver; on Linux ensure the GPU driver and libGL are installed."),
            new Rule("unsupportedclassversionerror",
                    "A class was compiled for a newer Java than the one running the game.",
                    "Run the game on the Java version the mod targets (Java 21 or newer)."),
            new Rule("nosuchmethoderror",
                    "A method the mod expects is missing — usually a version mismatch.",
                    "Make sure Fabric API and the client are the versions for this Minecraft release."),
            new Rule("noclassdeffounderror",
                    "A required class was not found on the classpath.",
                    "Reinstall the missing dependency (often Fabric API) and check for a corrupted download."),
            new Rule("modresolutionexception",
                    "Fabric Loader could not satisfy the mod dependency graph.",
                    "Read the loader message: it names the missing or conflicting mod and the version it needs."),
            new Rule("mixin apply failed",
                    "A mixin could not be applied — usually two mods patch the same method.",
                    "Remove or update the other mod that touches the same class; check the mixin's target."),
            new Rule("json syntaxexception",
                    "A configuration file is not valid JSON.",
                    "Delete the offending file named in the message; the client will recreate defaults."),
            new Rule("malformed",
                    "A file could not be parsed.",
                    "Recreate the affected config file; a .bak backup is kept next to it."),
            new Rule("baritone",
                    "The failure involves the optional Baritone integration.",
                    "Open the Baritone Assistant: check availability, version and the last integration error."),
            new Rule("concurrentmodificationexception",
                    "A collection was modified while it was being iterated.",
                    "Report this; it usually means a module changed shared state from a background thread."),
            new Rule("indexoutofboundsexception",
                    "Code indexed past the end of a list or array.",
                    "Report this with the crash fingerprint so the offending module can be fixed."),
            new Rule("nullpointerexception",
                    "A value that was expected to exist was null.",
                    "Report this with the crash fingerprint; note what you were doing when it happened."));

    private CrashAnalyzer() {
    }

    /** Produces a deterministic diagnosis for {@code record}. Never returns null. */
    public static Diagnosis analyze(CrashRecord record) {
        if (record == null) {
            return new Diagnosis("", CrashCategory.UNKNOWN, CrashSeverity.MEDIUM,
                    Diagnosis.Confidence.UNKNOWN, "No crash record was available.",
                    List.of(), List.of(), List.of(), "", List.of(), false);
        }
        List<String> facts = new ArrayList<>();
        List<String> hypotheses = new ArrayList<>();
        List<String> fixes = new ArrayList<>();

        facts.add("Exception: " + record.exceptionType()
                + (record.exceptionMessage().isBlank() ? "" : " — " + record.exceptionMessage()));
        facts.add("Category: " + record.category().label() + " · severity " + record.severity().label());
        facts.add("Source: " + record.source());
        if (!record.suspectedModule().isBlank()) {
            facts.add("Client frame names: " + record.suspectedModule());
        }
        if (!record.baritoneStatus().isBlank() && !record.baritoneStatus().equals("unknown")) {
            facts.add("Baritone at crash: " + record.baritoneStatus());
        }

        List<String> keyFrames = extractClientFrames(record.rawStackTrace());
        if (!keyFrames.isEmpty()) {
            facts.add("Relevant stack frames:");
            for (String frame : keyFrames) {
                facts.add("  " + frame);
            }
        }

        // Deterministic known-error matching over the whole record text.
        String haystack = (record.exceptionType() + " " + record.exceptionMessage() + " "
                + record.rawStackTrace()).toLowerCase(Locale.ROOT);
        String cause = null;
        for (Rule rule : KNOWN) {
            if (haystack.contains(rule.needle())) {
                cause = rule.cause();
                fixes.add(rule.fix());
                hypotheses.add("Matches a known error signature: \"" + rule.needle() + "\".");
                break;
            }
        }
        if (cause == null) {
            cause = "No known error signature matched. The cause is not confirmed.";
            hypotheses.add("This failure is not one of the recognised patterns; treat the cause as unconfirmed.");
        }

        // Category-specific guidance on top of the signature match.
        fixes.addAll(categoryGuidance(record.category()));

        Diagnosis.Confidence confidence = cause.startsWith("No known")
                ? Diagnosis.Confidence.POSSIBLE
                : Diagnosis.Confidence.LIKELY;
        if (record.category() == CrashCategory.OUT_OF_MEMORY) {
            confidence = Diagnosis.Confidence.CONFIRMED;
        }

        return new Diagnosis(record.fingerprint(), record.category(), record.severity(), confidence,
                cause, facts, hypotheses, dedupe(fixes), record.suspectedModule(), keyFrames, false);
    }

    private static List<String> categoryGuidance(CrashCategory category) {
        return switch (category) {
            case OUT_OF_MEMORY -> List.of("Close other applications and raise -Xmx in the launcher.");
            case RENDER_DRIVER -> List.of("Disable shaders and lower graphics settings, then restart the game.");
            case MOD_INCOMPATIBILITY -> List.of("Check for updates to every mod listed in the crash report.");
            case FABRIC_LOADER -> List.of("Re-run the Fabric installer for this Minecraft version.");
            case MODULE_EXCEPTION -> List.of("Toggle the named module off in the ClickGUI and report the fingerprint.");
            case BARITONE -> List.of("Check the Baritone Assistant for version and compatibility.");
            case CONFIG -> List.of("Remove the named config file; defaults will be recreated.");
            case WORLD_STATE -> List.of("Return to the main menu and rejoin the world.");
            case UNKNOWN -> List.of("Attach the full crash report when reporting this issue.");
        };
    }

    /** The first few client-owned frames, cleaned of any leading "at ". */
    static List<String> extractClientFrames(String stackTrace) {
        List<String> frames = new ArrayList<>();
        if (stackTrace == null) {
            return frames;
        }
        for (String line : stackTrace.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("com.logvex.crystal") && !trimmed.contains(".ai.")) {
                frames.add(trimmed.startsWith("at ") ? trimmed.substring(3) : trimmed);
                if (frames.size() >= 6) {
                    break;
                }
            }
        }
        return frames;
    }

    private static List<String> dedupe(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }
}
