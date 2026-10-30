package com.logvex.crystal.ai.crash;

import java.util.Locale;

/**
 * Deterministic, rule-based classification of a failure. This is the first line of the crash
 * analyzer and it never needs a model: it maps an exception type plus the first frames of the
 * stack to a {@link CrashCategory}, a {@link CrashSeverity} and (when the stack names one of
 * our modules) the suspected module.
 *
 * <p>Order matters: the most specific signatures are tested first. Every rule is a plain
 * string test, so it is cheap enough to run inside the crash handler and trivial to unit test.
 */
public final class CrashClassifier {

    private static final String CLIENT_PACKAGE = "com.logvex.crystal";

    private CrashClassifier() {
    }

    /** Immutable classification result. */
    public record Result(CrashCategory category, CrashSeverity severity, String suspectedModule) {
    }

    public static Result classify(Throwable error) {
        if (error == null) {
            return new Result(CrashCategory.UNKNOWN, CrashSeverity.MEDIUM, "");
        }
        StringBuilder haystack = new StringBuilder(512);
        haystack.append(error.getClass().getName()).append('\n');
        if (error.getMessage() != null) {
            haystack.append(error.getMessage()).append('\n');
        }
        StackTraceElement[] trace = error.getStackTrace();
        if (trace != null) {
            for (StackTraceElement frame : trace) {
                haystack.append(frame.getClassName()).append('#').append(frame.getMethodName()).append('\n');
            }
        }
        return classifyText(haystack.toString());
    }

    /**
     * Classifies from an already-flattened text blob (used for parsed crash reports where
     * there is no live {@link Throwable}). The blob should contain the exception type,
     * message and stack frames.
     */
    public static Result classifyText(String blob) {
        String original = blob == null ? "" : blob;
        String text = original.toLowerCase(Locale.ROOT);

        // --- memory ---
        if (contains(text, "outofmemoryerror", "java heap space", "gc overhead limit", "direct buffer memory")) {
            return new Result(CrashCategory.OUT_OF_MEMORY, CrashSeverity.CRITICAL, moduleIn(original));
        }

        // --- render / driver ---
        if (contains(text, "opengl", "glfw", "renderer", "shader", "org.lwjgl", "gl_", "irrlicht",
                "pixel format", "unsupported graphics", "gldebug", "glstate", "vertexformat")) {
            return new Result(CrashCategory.RENDER_DRIVER, CrashSeverity.HIGH, moduleIn(original));
        }

        // --- Fabric loader / mod resolution ---
        if (contains(text, "fabricloader", "net.fabricmc.loader", "modresolutionexception",
                "duplicate mod", "incompatible mod", "requires version", "broken mod",
                "mixin apply failed", "mixinapplyexception", "mixin transformation")) {
            return new Result(CrashCategory.MOD_INCOMPATIBILITY, CrashSeverity.HIGH, moduleIn(original));
        }

        // --- Baritone ---
        if (contains(text, "baritone")) {
            return new Result(CrashCategory.BARITONE, CrashSeverity.MEDIUM, moduleIn(original));
        }

        // --- config ---
        if (contains(text, "json", "gson", "malformed", "config", "syntax error", "end of input",
                "expected name", "unexpected token")) {
            return new Result(CrashCategory.CONFIG, CrashSeverity.LOW, moduleIn(original));
        }

        // --- world / state ---
        if (contains(text, "chunk", "world", "entity", "blockpos", "nullpointerexception", "indexoutofbounds",
                "illegalstate", "concurrentmodification")) {
            return new Result(CrashCategory.WORLD_STATE, CrashSeverity.MEDIUM, moduleIn(original));
        }

        // --- our own module threw ---
        String module = moduleIn(original);
        if (!module.isEmpty()) {
            return new Result(CrashCategory.MODULE_EXCEPTION, CrashSeverity.MEDIUM, module);
        }

        return new Result(CrashCategory.UNKNOWN, CrashSeverity.MEDIUM, "");
    }

    /**
     * The client module named by the first client-owned frame, or empty when none appears.
     * A frame like {@code com.logvex.crystal.module.combat.AutoCrystalModule#tick} yields
     * {@code AutoCrystalModule} (original case). Infrastructure packages (client/gui/util/
     * config/ai) are skipped so we name a real module, not a service.
     */
    static String moduleIn(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = lower.indexOf(CLIENT_PACKAGE, from);
            if (index < 0) {
                return "";
            }
            int end = index;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n' || c == '#' || c == '$' || c == '(' || c == ' ') {
                    break;
                }
                end++;
            }
            String frameClass = text.substring(index, end);
            int dot = frameClass.lastIndexOf('.');
            String simple = dot < 0 ? frameClass : frameClass.substring(dot + 1);
            String pkg = dot < 0 ? "" : frameClass.substring(0, dot).toLowerCase(Locale.ROOT);
            boolean infrastructure = pkg.endsWith(".client") || pkg.contains(".gui")
                    || pkg.contains(".util") || pkg.contains(".config") || pkg.contains(".ai")
                    || pkg.contains(".logging") || pkg.contains(".event");
            if (!simple.isBlank() && !infrastructure) {
                return simple;
            }
            from = Math.max(end, index + 1);
        }
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
