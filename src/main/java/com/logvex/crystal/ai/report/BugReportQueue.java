package com.logvex.crystal.ai.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ConfigIo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bounded, persisted queue of prepared bug reports.
 *
 * <p>Reports survive a restart so a crash at shutdown can still be reviewed and sent next launch.
 * The queue is deduplicated by fingerprint: a report for a crash already queued or sent is not
 * added again, which is the flood control the spec requires. The store uses the shared atomic
 * write, and the file can be deleted from the UI.
 */
public final class BugReportQueue {

    /** Bumped when the persisted shape changes. */
    public static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final int capacity;
    private final List<BugReport> reports = new ArrayList<>();

    public BugReportQueue(Path file, int capacity) {
        this.file = file;
        this.capacity = Math.max(1, capacity);
    }

    public Path file() {
        return file;
    }

    /** Loads the persisted queue; refuses a newer schema rather than misreading it. */
    public void load() {
        reports.clear();
        JsonObject root = ConfigIo.readObject(file);
        if (root == null) {
            return;
        }
        int schema = root.has("schema") ? root.get("schema").getAsInt() : SCHEMA_VERSION;
        if (schema > SCHEMA_VERSION) {
            return;
        }
        if (root.has("reports") && root.get("reports").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("reports")) {
                if (element.isJsonObject()) {
                    reports.add(fromJson(element.getAsJsonObject()));
                }
            }
        }
        trim();
    }

    public void save() throws IOException {
        ConfigIo.writeObject(file, toJson());
    }

    /**
     * Adds a report unless one for the same fingerprint is already queued (or has been sent).
     * Returns true when it was added.
     */
    public synchronized boolean add(BugReport report) {
        if (report == null || report.fingerprint().isBlank()) {
            return false;
        }
        for (BugReport existing : reports) {
            if (existing.fingerprint().equals(report.fingerprint())) {
                return false;
            }
        }
        reports.add(report);
        trim();
        return true;
    }

    /** Replaces the report with the same id (e.g. to update its delivery status). */
    public synchronized boolean update(BugReport report) {
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).id().equals(report.id())) {
                reports.set(i, report);
                return true;
            }
        }
        return false;
    }

    public synchronized List<BugReport> all() {
        return new ArrayList<>(reports);
    }

    public synchronized BugReport byId(String id) {
        for (BugReport report : reports) {
            if (report.id().equals(id)) {
                return report;
            }
        }
        return null;
    }

    /** Reports still waiting to be sent (or retried). */
    public synchronized List<BugReport> pending() {
        List<BugReport> pending = new ArrayList<>();
        for (BugReport report : reports) {
            if (report.status() == BugReport.Status.PENDING || report.status() == BugReport.Status.FAILED) {
                pending.add(report);
            }
        }
        return pending;
    }

    public synchronized boolean remove(String id) {
        return reports.removeIf(report -> report.id().equals(id));
    }

    public synchronized void clear() {
        reports.clear();
    }

    public synchronized int size() {
        return reports.size();
    }

    /** True when a report for this fingerprint has already been queued or sent. */
    public synchronized boolean containsFingerprint(String fingerprint) {
        for (BugReport report : reports) {
            if (report.fingerprint().equals(fingerprint)
                    && report.status() != BugReport.Status.DECLINED) {
                return true;
            }
        }
        return false;
    }

    private void trim() {
        while (reports.size() > capacity) {
            reports.remove(0);
        }
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (BugReport report : reports) {
            JsonObject o = new JsonObject();
            o.addProperty("id", report.id());
            o.addProperty("fingerprint", report.fingerprint());
            o.addProperty("created", report.createdMillis());
            o.addProperty("subject", report.subject());
            o.addProperty("body", report.body());
            o.addProperty("status", report.status().name());
            o.addProperty("attempts", report.attempts());
            o.addProperty("lastError", report.lastError());
            array.add(o);
        }
        root.add("reports", array);
        return root;
    }

    private static BugReport fromJson(JsonObject o) {
        BugReport.Status status;
        try {
            status = BugReport.Status.valueOf(str(o, "status"));
        } catch (RuntimeException e) {
            status = BugReport.Status.PENDING;
        }
        return new BugReport(str(o, "id"), str(o, "fingerprint"),
                o.has("created") ? o.get("created").getAsLong() : 0L,
                str(o, "subject"), str(o, "body"), status,
                o.has("attempts") ? o.get("attempts").getAsInt() : 0, str(o, "lastError"));
    }

    private static String str(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Fingerprints already present, used by diagnostics. */
    public synchronized Map<String, Integer> fingerprintCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BugReport report : reports) {
            counts.merge(report.fingerprint(), 1, Integer::sum);
        }
        return counts;
    }
}
