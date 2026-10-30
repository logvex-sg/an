package com.logvex.crystal.ai.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists captured crashes to a single JSON file so a crash that happened at shutdown can be
 * analysed on the next launch. Uses the shared {@link ConfigIo} atomic-write path, so a crash
 * during a write cannot corrupt the file.
 *
 * <p>The stored stack trace is the raw one; redaction happens when a report is built, not here,
 * so local analysis still has the full detail. The file lives under the client's config
 * directory and can be deleted from the UI.
 */
public final class CrashStore {

    /** Bumped when the persisted shape changes. */
    public static final int SCHEMA_VERSION = 1;

    /** Cap on stored stack-trace characters, so one giant trace cannot bloat the file. */
    private static final int MAX_TRACE_CHARS = 24_000;

    private final Path file;

    public CrashStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Loads the stored history, or an empty list when the file is absent or unreadable. */
    public List<CrashRecord> load() {
        JsonObject root = ConfigIo.readObject(file);
        List<CrashRecord> records = new ArrayList<>();
        if (root == null) {
            return records;
        }
        int schema = root.has("schema") ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return records; // newer file: refuse rather than misread
        }
        if (root.has("crashes") && root.get("crashes").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("crashes")) {
                if (element.isJsonObject()) {
                    records.add(fromJson(element.getAsJsonObject()));
                }
            }
        }
        return records;
    }

    /** Writes the history atomically. */
    public void save(List<CrashRecord> records) throws IOException {
        ConfigIo.writeObject(file, toJson(records));
    }

    public static JsonObject toJson(List<CrashRecord> records) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        if (records != null) {
            for (CrashRecord record : records) {
                array.add(toJson(record));
            }
        }
        root.add("crashes", array);
        return root;
    }

    private static JsonObject toJson(CrashRecord r) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id());
        o.addProperty("fingerprint", r.fingerprint());
        o.addProperty("timestamp", r.timestampMillis());
        o.addProperty("exceptionType", r.exceptionType());
        o.addProperty("exceptionMessage", r.exceptionMessage());
        String trace = r.rawStackTrace();
        if (trace.length() > MAX_TRACE_CHARS) {
            trace = trace.substring(0, MAX_TRACE_CHARS) + "\n… (truncated)";
        }
        o.addProperty("stackTrace", trace);
        o.addProperty("category", r.category().name());
        o.addProperty("severity", r.severity().name());
        o.addProperty("suspectedModule", r.suspectedModule());
        o.addProperty("source", r.source());
        o.addProperty("modVersion", r.modVersion());
        o.addProperty("minecraftVersion", r.minecraftVersion());
        o.addProperty("loaderVersion", r.loaderVersion());
        o.addProperty("javaVersion", r.javaVersion());
        o.addProperty("osName", r.osName());
        o.addProperty("baritoneStatus", r.baritoneStatus());
        o.addProperty("aiStatus", r.aiStatus());
        JsonArray events = new JsonArray();
        for (String event : r.recentEvents()) {
            events.add(event);
        }
        o.add("recentEvents", events);
        return o;
    }

    public static CrashRecord fromJson(JsonObject o) {
        CrashRecord.Builder b = CrashRecord.builder()
                .id(str(o, "id"))
                .fingerprint(str(o, "fingerprint"))
                .timestampMillis(o.has("timestamp") ? o.get("timestamp").getAsLong() : 0L)
                .exceptionType(str(o, "exceptionType"))
                .exceptionMessage(str(o, "exceptionMessage"))
                .rawStackTrace(str(o, "stackTrace"))
                .category(parseCategory(str(o, "category")))
                .severity(parseSeverity(str(o, "severity")))
                .suspectedModule(str(o, "suspectedModule"))
                .source(str(o, "source"))
                .modVersion(str(o, "modVersion"))
                .minecraftVersion(str(o, "minecraftVersion"))
                .loaderVersion(str(o, "loaderVersion"))
                .javaVersion(str(o, "javaVersion"))
                .osName(str(o, "osName"))
                .baritoneStatus(str(o, "baritoneStatus"))
                .aiStatus(str(o, "aiStatus"));
        if (o.has("recentEvents") && o.get("recentEvents").isJsonArray()) {
            List<String> events = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("recentEvents")) {
                events.add(e.getAsString());
            }
            b.recentEvents(events);
        }
        return b.build();
    }

    private static String str(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static CrashCategory parseCategory(String name) {
        try {
            return CrashCategory.valueOf(name);
        } catch (RuntimeException e) {
            return CrashCategory.UNKNOWN;
        }
    }

    private static CrashSeverity parseSeverity(String name) {
        try {
            return CrashSeverity.valueOf(name);
        } catch (RuntimeException e) {
            return CrashSeverity.MEDIUM;
        }
    }
}
