package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.ai.AISettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round-trip and resilience tests for the mini-AI config codec. */
class AISettingsStoreTest {

    @Test
    void roundTripsEveryField(@TempDir Path dir) throws IOException {
        AISettingsStore store = new AISettingsStore(dir.resolve("ai.json"));
        AISettings original = new AISettings();
        original.enabled = false;
        original.crashDetection = false;
        original.localAnalysis = true;
        original.reportQueue = false;
        original.emailEnabled = true;
        original.askBeforeSending = false;
        original.includeEnvironment = false;
        original.includeLogs = false;
        original.keepLocalHistory = false;
        original.catCosmetics = false;
        original.extraRedactions = "Alice\nBob";
        original.maxReportKilobytes = 128;
        original.maxHistory = 25;
        original.modelId = "smollm2-360m-instruct-q4_k_m";
        original.idleUnloadMinutes = 30;
        original.inferenceThreads = 2;
        original.pendingQueueLimit = 3;
        original.inferenceTimeoutSeconds = 45;
        original.smtpHost = "smtp.example.com";
        original.smtpPort = 465;
        original.smtpUser = "user@example.com";
        original.smtpPassword = "hunter2";
        original.smtpStartTls = false;
        original.smtpFrom = "bot@example.com";

        store.save(original);
        AISettings loaded = new AISettings();
        store.loadInto(loaded);

        assertFalse(loaded.enabled);
        assertFalse(loaded.crashDetection);
        assertTrue(loaded.localAnalysis);
        assertFalse(loaded.reportQueue);
        assertTrue(loaded.emailEnabled);
        assertFalse(loaded.askBeforeSending);
        assertFalse(loaded.includeEnvironment);
        assertFalse(loaded.includeLogs);
        assertFalse(loaded.keepLocalHistory);
        assertFalse(loaded.catCosmetics);
        assertEquals("Alice\nBob", loaded.extraRedactions);
        assertEquals(128, loaded.maxReportKilobytes);
        assertEquals(25, loaded.maxHistory);
        assertEquals("smollm2-360m-instruct-q4_k_m", loaded.modelId);
        assertEquals(30, loaded.idleUnloadMinutes);
        assertEquals(2, loaded.inferenceThreads);
        assertEquals(3, loaded.pendingQueueLimit);
        assertEquals(45, loaded.inferenceTimeoutSeconds);
        assertEquals("smtp.example.com", loaded.smtpHost);
        assertEquals(465, loaded.smtpPort);
        assertEquals("user@example.com", loaded.smtpUser);
        assertEquals("hunter2", loaded.smtpPassword);
        assertFalse(loaded.smtpStartTls);
        assertEquals("bot@example.com", loaded.smtpFrom);
    }

    @Test
    void absentFileKeepsDefaults(@TempDir Path dir) {
        AISettingsStore store = new AISettingsStore(dir.resolve("missing.json"));
        AISettings settings = new AISettings();
        settings.enabled = false;
        store.loadInto(settings);
        assertFalse(settings.enabled, "an absent file must not change a live value");
    }

    @Test
    void corruptFileKeepsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ai.json");
        Files.writeString(file, "{ not json at all");
        AISettings settings = new AISettings();
        new AISettingsStore(file).loadInto(settings);
        assertTrue(settings.enabled);
        assertFalse(settings.localAnalysis);
    }

    @Test
    void newerSchemaIsRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ai.json");
        Files.writeString(file, "{\"schema\":" + (AISettingsStore.SCHEMA_VERSION + 1)
                + ",\"enabled\":false}");
        AISettings settings = new AISettings();
        new AISettingsStore(file).loadInto(settings);
        assertTrue(settings.enabled, "a future schema must be ignored, not half-read");
    }

    @Test
    void loadClampsOutOfRangeValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ai.json");
        Files.writeString(file, "{\"schema\":1,\"inferenceThreads\":99,\"smtpPort\":-4,"
                + "\"pendingQueueLimit\":0}");
        AISettings settings = new AISettings();
        new AISettingsStore(file).loadInto(settings);
        assertEquals(AISettings.MAX_THREADS, settings.inferenceThreads);
        assertEquals(1, settings.smtpPort);
        assertEquals(AISettings.MIN_QUEUE, settings.pendingQueueLimit);
    }
}
