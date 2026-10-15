package com.logvex.crystal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared, Minecraft-independent helpers for the JSON config stores.
 *
 * <p>Every store previously re-implemented the same atomic-write dance. Centralising it
 * gives one place to get the safety rules right: write to a temp file, flush and close it,
 * then move it over the target; before that, keep a {@code .bak} copy of the previous valid
 * file so a bad write or a crash cannot lose the user's settings. Reads tolerate a missing,
 * empty or malformed file by returning {@code null} so the caller can fall back to defaults.
 */
public final class ConfigIo {

    /** Pretty-printing Gson shared by the stores (no HTML escaping, so names stay readable). */
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";

    /**
     * A process-wide sink for "a config file could not be read, so defaults were used"
     * notices. The client registers this to raise an in-game warning once at startup, so a
     * corrupt file is reported to the user rather than silently reset. Deliberately
     * Minecraft-independent, so the store tests can observe it too.
     */
    private static volatile java.util.function.Consumer<String> recoveryListener;

    /** Registers the recovery listener (last registration wins). */
    public static void onRecovery(java.util.function.Consumer<String> listener) {
        recoveryListener = listener;
    }

    private ConfigIo() {
    }

    /**
     * Reads and parses a JSON object from {@code file}.
     *
     * @return the parsed object, or {@code null} when the file is absent, empty, malformed
     *         or not a JSON object. Never throws. A file that exists but cannot be parsed
     *         fires the recovery listener so the user learns defaults were used.
     */
    public static JsonObject readObject(Path file) {
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonObject object = root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
            if (object == null) {
                reportRecovery(file);
            }
            return object;
        } catch (Exception e) {
            reportRecovery(file);
            return null;
        }
    }

    private static void reportRecovery(Path file) {
        java.util.function.Consumer<String> listener = recoveryListener;
        if (listener != null) {
            try {
                listener.accept(file.getFileName().toString());
            } catch (RuntimeException ignored) {
                // A broken listener must never break a load.
            }
        }
    }

    /**
     * Writes {@code root} to {@code file} atomically and durably.
     *
     * <p>Sequence: create the parent directory, snapshot the current file to {@code .bak},
     * write the temp file and close it (flushing), then move the temp file over the target
     * (atomic when the filesystem supports it). A failure at any step leaves either the old
     * file or the backup intact, so the next launch still has something to load.
     */
    public static void writeObject(Path file, JsonObject root) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        backup(file);
        Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies the current file to {@code <name>.bak} when it exists, so a corrupt write can be
     * recovered by hand. Best effort: a backup failure must not block the real write.
     */
    private static void backup(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + BACKUP_SUFFIX),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // A missing backup is not fatal; the write itself still proceeds.
        }
    }

    /** The backup path a store would keep for {@code file} ({@code <name>.bak}). */
    public static Path backupPath(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }
}
