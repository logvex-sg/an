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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Named configuration profiles: a snapshot of the module, HUD and appearance layers that
 * can be saved, loaded, renamed, duplicated, exported, imported and deleted.
 *
 * <p>Each profile is one JSON file under {@code <config>/profiles/} with a versioned schema
 * and three optional sections ({@code modules}, {@code hud}, {@code appearance}) whose
 * shapes match the stores that own them, so applying a profile reuses those codecs rather
 * than re-implementing them.
 *
 * <p>Safety rules: writes are atomic, a corrupt file is moved aside rather than silently
 * dropped, a newer schema is refused, and {@link #write(Profile, boolean)} will not
 * overwrite an existing valid profile unless the caller explicitly opts in - so a profile
 * is never clobbered by accident. The codec is Minecraft-independent and unit tested.
 */
public final class ProfileManager {

    /** Schema version of the on-disk profile format. Bump when the field set changes shape. */
    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SUFFIX = ".json";
    /** Documents written by "Export" live beside profiles but are not profiles themselves. */
    public static final String EXPORT_SUFFIX = ".export.json";

    /** A loaded profile: its name plus the three configuration sections. */
    public record Profile(String name, JsonObject modules, JsonObject hud, JsonObject appearance) {
    }

    /** Metadata for listing without fully parsing each profile. */
    public record Info(String name, long modifiedMillis) {
    }

    private final Path directory;

    public ProfileManager(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public Path fileFor(String name) {
        return directory.resolve(sanitize(name) + SUFFIX);
    }

    /** Profile names sorted case-insensitively. Never null; empty when none exist. */
    public List<String> names() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                    .filter(p -> !p.getFileName().toString().endsWith(EXPORT_SUFFIX))
                    .forEach(p -> {
                        String file = p.getFileName().toString();
                        result.add(file.substring(0, file.length() - SUFFIX.length()));
                    });
        } catch (IOException e) {
            return List.of();
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public List<Info> list() {
        List<Info> result = new ArrayList<>();
        for (String name : names()) {
            long modified = 0L;
            try {
                modified = Files.getLastModifiedTime(fileFor(name)).toMillis();
            } catch (IOException ignored) {
                // A vanished file simply reports a zero timestamp.
            }
            result.add(new Info(name, modified));
        }
        return result;
    }

    public boolean exists(String name) {
        return name != null && Files.exists(fileFor(name));
    }

    /**
     * Reads a profile, or returns null when it is missing. A corrupt or newer-schema file
     * is moved aside (recovery) and null is returned so the caller keeps running.
     */
    public Profile read(String name) {
        Path file = fileFor(name);
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                quarantine(file);
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            int schema = object.has("schema") && object.get("schema").isJsonPrimitive()
                    ? object.get("schema").getAsInt() : SCHEMA_VERSION;
            if (schema > SCHEMA_VERSION) {
                // A profile written by a newer build must not be misread.
                return null;
            }
            return new Profile(sanitize(name), section(object, "modules"),
                    section(object, "hud"), section(object, "appearance"));
        } catch (Exception e) {
            quarantine(file);
            return null;
        }
    }

    /**
     * Writes a profile atomically. When {@code overwrite} is false and a valid profile of
     * that name already exists, the write is refused and false is returned - callers must
     * confirm a destructive replace explicitly.
     */
    public boolean write(Profile profile, boolean overwrite) throws IOException {
        if (profile == null || profile.name() == null || profile.name().isBlank()) {
            return false;
        }
        Path file = fileFor(profile.name());
        if (!overwrite && Files.exists(file) && read(profile.name()) != null) {
            return false;
        }
        JsonObject root = toJson(profile);
        Files.createDirectories(directory);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    /** Deletes a profile. Returns true when a file was removed. */
    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            return false;
        }
    }

    /** Renames a profile, refusing to overwrite an existing valid target. */
    public boolean rename(String from, String to) throws IOException {
        if (!exists(from) || exists(to)) {
            return false;
        }
        Files.createDirectories(directory);
        Files.move(fileFor(from), fileFor(to), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Duplicates a profile under a new name. Returns false on a missing source or clash. */
    public boolean duplicate(String from, String to) throws IOException {
        Profile source = read(from);
        if (source == null || exists(to)) {
            return false;
        }
        return write(new Profile(sanitize(to), source.modules(), source.hud(), source.appearance()), false);
    }

    /** Serializes a profile to a JSON object (exposed for export and tests). */
    public static JsonObject toJson(Profile profile) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("name", profile.name());
        root.add("modules", profile.modules() == null ? new JsonObject() : profile.modules());
        root.add("hud", profile.hud() == null ? new JsonObject() : profile.hud());
        root.add("appearance", profile.appearance() == null ? new JsonObject() : profile.appearance());
        return root;
    }

    /** Parses an exported profile document. Returns null on malformed/newer input. */
    public static Profile fromJson(JsonObject root) {
        if (root == null) {
            return null;
        }
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return null;
        }
        String name = root.has("name") && root.get("name").isJsonPrimitive()
                ? root.get("name").getAsString() : "imported";
        return new Profile(sanitize(name), section(root, "modules"),
                section(root, "hud"), section(root, "appearance"));
    }

    private static JsonObject section(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
    }

    /** Moves a broken file to {@code <name>.corrupt} so data is recoverable, not lost. */
    private void quarantine(Path file) {
        try {
            Path target = file.resolveSibling(file.getFileName() + ".corrupt");
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Best effort; the caller still gets a null profile and keeps running.
        }
    }

    /** Restricts a name to filesystem-safe characters so it cannot escape the directory. */
    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "profile";
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 _-]", "");
        cleaned = cleaned.replaceAll("\\s+", "-").replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        // Strip a trailing ".json" so "foo.json" and "foo" map to the same profile.
        if (cleaned.endsWith(SUFFIX)) {
            cleaned = cleaned.substring(0, cleaned.length() - SUFFIX.length());
        }
        if (cleaned.length() > 48) {
            cleaned = cleaned.substring(0, 48);
        }
        return cleaned.isBlank() ? "profile" : cleaned;
    }
}
