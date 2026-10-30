package com.logvex.crystal.ai.crash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Produces a stable fingerprint for a failure so the same crash is recognised across
 * launches (duplicate suppression, "seen N times", flood control).
 *
 * <p>The fingerprint is derived from the exception type, the top of the stack trace (the
 * first few frames below the client), and the exception message with volatile parts
 * (addresses, numbers, paths) removed. It deliberately excludes the timestamp, so a crash
 * that recurs every launch keeps one fingerprint.
 *
 * <p>Pure and Minecraft-independent, so it is unit tested directly.
 */
public final class CrashFingerprint {

    /** How many stack frames participate in the fingerprint. */
    private static final int FRAME_DEPTH = 6;

    private CrashFingerprint() {
    }

    /** A 16-character hex fingerprint for {@code error}. Never null; never throws. */
    public static String of(Throwable error) {
        StringBuilder material = new StringBuilder(256);
        String type = error == null ? "null" : error.getClass().getName();
        material.append(type).append('\n');
        material.append(normalize(error == null ? "" : error.getMessage())).append('\n');
        StackTraceElement[] trace = error == null ? null : error.getStackTrace();
        if (trace != null) {
            int used = 0;
            for (StackTraceElement frame : trace) {
                // Only the client's own frames and the frames directly around them are stable
                // across machines; skip line numbers so a recompile does not change the hash.
                material.append(frame.getClassName()).append('#').append(frame.getMethodName()).append('\n');
                if (++used >= FRAME_DEPTH) {
                    break;
                }
            }
        }
        return sha256Hex16(material.toString());
    }

    /**
     * A fingerprint for an already-textual signature (used for parsed crash reports and
     * error-log lines). Callers pass a stable signature string; normalisation removes the
     * volatile parts.
     */
    public static String ofSignature(String signature) {
        return sha256Hex16(normalize(signature == null ? "" : signature));
    }

    /** Collapses volatile tokens (hex ids, numbers, quoted paths, memory sizes) so the hash is stable. */
    static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String s = message.toLowerCase(Locale.ROOT);
        s = s.replaceAll("0x[0-9a-f]+", "0x#");            // pointers / object ids
        s = s.replaceAll("\\b\\d{3,}\\b", "#");             // long numbers (sizes, ticks, ports)
        s = s.replaceAll("\\d+(?:\\.\\d+)?(?:ms|mb|gb|kb|fps)", "#"); // measurements
        s = s.replaceAll("[a-z]:\\\\[^\\s]+", "path");      // windows paths
        s = s.replaceAll("/(?:[\\w.-]+/)+[\\w.-]+", "path"); // unix paths
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private static String sha256Hex16(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < hash.length; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; this cannot happen on a conformant JVM.
            return Integer.toHexString(text.hashCode());
        }
    }
}
