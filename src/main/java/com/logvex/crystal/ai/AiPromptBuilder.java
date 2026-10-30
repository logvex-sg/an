package com.logvex.crystal.ai;

import com.logvex.crystal.ai.baritone.BaritoneAssistant;
import com.logvex.crystal.ai.crash.CrashRecord;
import com.logvex.crystal.ai.crash.Diagnosis;
import com.logvex.crystal.ai.privacy.PrivacyFilter;
import com.logvex.crystal.integration.baritone.BaritoneStatus;

/**
 * Builds the short prompts the local model answers. Kept separate and pure so the exact text is
 * unit-testable and auditable: every prompt is built from a privacy-filtered summary, never the
 * raw crash record, and the model is told to stay brief and never invent a cause.
 */
public final class AiPromptBuilder {

    /** Keeps prompts small so a 0.5B model stays fast and on-topic. */
    private static final int MAX_PROMPT_CHARS = 1200;

    private final PrivacyFilter filter;

    public AiPromptBuilder(PrivacyFilter filter) {
        this.filter = filter == null ? new PrivacyFilter() : filter;
    }

    /** A prompt asking for a one-paragraph explanation of a crash. */
    public String crashPrompt(CrashRecord record, Diagnosis diagnosis) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("You are a concise assistant inside a Minecraft client. ")
                .append("Explain the likely cause of this crash in at most three short sentences. ")
                .append("Do not invent details and do not repeat the stack trace.\n\n");
        prompt.append("Category: ").append(record.category().label()).append('\n');
        prompt.append("Exception: ").append(record.exceptionType());
        if (!record.exceptionMessage().isBlank()) {
            prompt.append(" - ").append(record.exceptionMessage());
        }
        prompt.append('\n');
        if (diagnosis != null && !diagnosis.keyFrames().isEmpty()) {
            prompt.append("Top frames:\n");
            for (String frame : diagnosis.keyFrames()) {
                prompt.append("  ").append(frame).append('\n');
            }
        }
        return cap(filter.filter(prompt.toString()));
    }

    /** A prompt asking what to check when Baritone is misbehaving. */
    public String baritonePrompt(BaritoneStatus status, String lastError) {
        StringBuilder prompt = new StringBuilder(384);
        prompt.append("You are a concise assistant inside a Minecraft client. ")
                .append("In at most three short sentences, suggest what to check next. ")
                .append("Do not invent versions or commands.\n\n");
        prompt.append("Baritone: ").append(BaritoneAssistant.reportSummary(status, lastError)).append('\n');
        return cap(filter.filter(prompt.toString()));
    }

    /** A prompt summarising the client's diagnostics. */
    public String diagnosticsPrompt(String diagnostics) {
        String prompt = "You are a concise assistant inside a Minecraft client. "
                + "In at most three short sentences, point out anything that looks wrong. "
                + "If nothing looks wrong, say so.\n\n" + diagnostics;
        return cap(filter.filter(prompt));
    }

    private static String cap(String prompt) {
        if (prompt.length() <= MAX_PROMPT_CHARS) {
            return prompt;
        }
        return prompt.substring(0, MAX_PROMPT_CHARS);
    }
}
