package com.logvex.crystal.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.ai.AISettings;
import com.logvex.crystal.ai.crash.CrashRecord;
import com.logvex.crystal.ai.crash.CrashSeverity;
import com.logvex.crystal.ai.crash.Diagnosis;
import com.logvex.crystal.ai.privacy.PrivacyFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The report builder: sanitization, size enforcement and structure. */
class BugReportBuilderTest {

    private CrashRecord record() {
        return CrashRecord.builder()
                .id("fp-1")
                .fingerprint("abc123")
                .timestampMillis(1_700_000_000_000L)
                .exceptionType("java.lang.IllegalStateException")
                .exceptionMessage("bad state")
                .rawStackTrace("at com.logvex.crystal.module.FooModule.tick(FooModule.java:1)\n"
                        + "token=deadbeefcafebabe should be redacted")
                .source("thread:Client")
                .modVersion("6.0.0")
                .minecraftVersion("1.21.11")
                .baritoneStatus("version=1.10.2, goal=none")
                .aiStatus("DISABLED")
                .recentEvents(List.of("module AutoCrystal enabled"))
                .build();
    }

    private Diagnosis diagnosis() {
        return new Diagnosis("abc123", com.logvex.crystal.ai.crash.CrashCategory.UNKNOWN,
                CrashSeverity.MEDIUM, Diagnosis.Confidence.POSSIBLE, "Something went wrong",
                List.of("fact"), List.of("guess"), List.of("do a thing"), "FooModule",
                List.of("FooModule.tick"), false);
    }

    @Test
    void buildsASanitizedStructuredReport() {
        BugReportBuilder builder = new BugReportBuilder(new PrivacyFilter(), new AISettings());
        BugReport report = builder.build(record(), diagnosis());

        assertTrue(report.subject().contains("abc123"));
        assertTrue(report.body().contains("-- Crash --"));
        assertTrue(report.body().contains("-- Environment --"));
        assertTrue(report.body().contains("-- Diagnosis (Possible) --"));
        assertTrue(report.body().contains("do a thing"));
        assertFalse(report.body().contains("deadbeefcafebabe"), "a token must be redacted");
        assertTrue(report.body().contains(PrivacyFilter.REDACTED));
        assertEquals(BugReport.Status.PENDING, report.status());
        assertEquals("abc123", report.fingerprint());
    }

    @Test
    void environmentAndLogsCanBeOmitted() {
        AISettings settings = new AISettings();
        settings.includeEnvironment = false;
        settings.includeLogs = false;
        BugReport report = new BugReportBuilder(new PrivacyFilter(), settings).build(record(), null);
        assertFalse(report.body().contains("-- Environment --"));
        assertFalse(report.body().contains("-- Recent events --"));
    }

    @Test
    void extraRedactionsAreApplied() {
        AISettings settings = new AISettings();
        settings.extraRedactions = "FooModule";
        BugReport report = new BugReportBuilder(new PrivacyFilter(), settings).build(record(), null);
        assertFalse(report.body().contains("FooModule"));
    }

    @Test
    void oversizedBodyIsTruncatedButHeaderKept() {
        String body = "HEADER\n" + "x".repeat(10_000);
        String enforced = BugReportBuilder.enforceMaxSize(body, 1);
        assertTrue(enforced.startsWith("HEADER"));
        assertTrue(enforced.contains("[report truncated"));
        assertTrue(enforced.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 1024 + 64);
    }

    @Test
    void recipientIsFixed() {
        assertEquals("matt.sgrblx@outlook.com", BugReportBuilder.recipient());
    }
}
