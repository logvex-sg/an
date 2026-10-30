package com.logvex.crystal.ai.crash;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The instance front end for crash analysis. The deterministic {@link CrashAnalyzer} always
 * runs; this adds an optional, clearly-labelled enhancement produced by the local model when the
 * user asked for one.
 *
 * <p>The enhancement never replaces the deterministic verdict: it is appended as an extra note
 * and the diagnosis is flagged {@code aiGenerated}, so the UI can say the explanation was
 * enhanced while the confirmed facts and fixes stay authoritative.
 */
public final class CrashAnalyzerService {

    private final Map<String, String> enhancements = new ConcurrentHashMap<>();

    /** A deterministic diagnosis, merged with any AI enhancement for the same fingerprint. */
    public Diagnosis diagnose(CrashRecord record) {
        Diagnosis base = CrashAnalyzer.analyze(record);
        String extra = record == null ? null : enhancements.get(record.fingerprint());
        if (extra == null || extra.isBlank()) {
            return base;
        }
        List<String> hypotheses = new ArrayList<>(base.hypotheses());
        hypotheses.add("Local model: " + extra.trim());
        return new Diagnosis(base.fingerprint(), base.category(), base.severity(), base.confidence(),
                base.likelyCause(), base.facts(), hypotheses, base.suggestedFixes(),
                base.suspectedModule(), base.keyFrames(), true);
    }

    /** Records a model explanation for a fingerprint. */
    public void enhance(String fingerprint, String text) {
        if (fingerprint != null && text != null && !text.isBlank()) {
            enhancements.put(fingerprint, text.trim());
        }
    }

    public boolean hasEnhancement(String fingerprint) {
        return fingerprint != null && enhancements.containsKey(fingerprint);
    }

    public void clearEnhancements() {
        enhancements.clear();
    }
}
